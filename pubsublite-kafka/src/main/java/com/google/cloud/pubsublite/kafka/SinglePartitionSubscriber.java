/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.pubsublite.kafka;

import static com.google.common.base.Preconditions.checkState;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.cloud.pubsublite.Offset;
import com.google.cloud.pubsublite.Partition;
import com.google.cloud.pubsublite.internal.BlockingPullSubscriber;
import com.google.cloud.pubsublite.internal.CheckedApiException;
import com.google.cloud.pubsublite.internal.CloseableMonitor;
import com.google.cloud.pubsublite.internal.ProxyService;
import com.google.cloud.pubsublite.internal.wire.Committer;
import com.google.cloud.pubsublite.proto.SeekRequest;
import com.google.cloud.pubsublite.proto.SequencedMessage;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayDeque;
import java.util.Optional;

/** Pulls messages and manages commits for a single partition of a subscription. */
class SinglePartitionSubscriber extends ProxyService {
  private final PullSubscriberFactory subscriberFactory;
  private final Partition partition;
  private final Committer committer;
  private final boolean enableReset;

  private final CloseableMonitor monitor = new CloseableMonitor();

  // New versions of ErrorProne do not recognize CloseableMonitor
  // @GuardedBy("monitor.monitor")
  private BlockingPullSubscriber subscriber;

  // @GuardedBy("monitor.monitor")
  private boolean needsCommitting = false;

  // @GuardedBy("monitor.monitor")
  private Optional<Offset> lastReceived = Optional.empty();

  SinglePartitionSubscriber(
      PullSubscriberFactory subscriberFactory,
      Partition partition,
      SeekRequest initialSeek,
      Committer committer,
      boolean enableReset)
      throws CheckedApiException {
    this.subscriberFactory = subscriberFactory;
    this.partition = partition;
    this.committer = committer;
    this.enableReset = enableReset;
    this.subscriber =
        subscriberFactory.newPullSubscriber(partition, initialSeek, this::onSubscriberReset);
    addServices(committer);
  }

  // ProxyService implementation.
  @Override
  protected void start() {}

  @Override
  protected void stop() {
    try (CloseableMonitor.Hold h = monitor.enter()) {
      subscriber.close();
    }
  }

  @Override
  protected void handlePermanentError(CheckedApiException error) {
    stop();
  }

  /** Executes a client-initiated seek. */
  void clientSeek(SeekRequest request) throws CheckedApiException {
    try (CloseableMonitor.Hold h = monitor.enter()) {
      subscriber.close();
      subscriber = subscriberFactory.newPullSubscriber(partition, request, this::onSubscriberReset);
    }
  }

  ApiFuture<Void> onData() {
    try (CloseableMonitor.Hold h = monitor.enter()) {
      return subscriber.onData();
    }
  }

  // @GuardedBy("monitor.monitor")
  private ArrayDeque<SequencedMessage> pullMessages() throws CheckedApiException {
    ArrayDeque<SequencedMessage> messages = new ArrayDeque<>();
    for (Optional<SequencedMessage> message = subscriber.messageIfAvailable();
        message.isPresent();
        message = subscriber.messageIfAvailable()) {
      messages.add(message.get());
    }
    return messages;
  }

  /** Pulls all available messages. */
  ArrayDeque<SequencedMessage> getMessages() throws CheckedApiException {
    try (CloseableMonitor.Hold h = monitor.enter()) {
      ArrayDeque<SequencedMessage> messages = pullMessages();
      if (!messages.isEmpty()) {
        lastReceived = Optional.of(Offset.of(Iterables.getLast(messages).getCursor().getOffset()));
        needsCommitting = true;
      }
      return messages;
    }
  }

  Optional<Long> position() {
    try (CloseableMonitor.Hold h = monitor.enter()) {
      return lastReceived.map(lastReceived -> lastReceived.value() + 1);
    }
  }

  /** Executes a client-initiated commit. */
  ApiFuture<Void> commitOffset(Offset offset) {
    return committer.commitOffset(offset);
  }

  /** Auto-commits the offset of the last received message. */
  Optional<ApiFuture<Offset>> autoCommit() {
    try (CloseableMonitor.Hold h = monitor.enter()) {
      if (!needsCommitting) return Optional.empty();
      checkState(lastReceived.isPresent());
      needsCommitting = false;
      // The Pub/Sub Lite commit offset is one more than the last received.
      Offset toCommit = Offset.of(lastReceived.get().value() + 1);
      return Optional.of(
          ApiFutures.transform(
              committer.commitOffset(toCommit),
              ignored -> toCommit,
              MoreExecutors.directExecutor()));
    }
  }

  private boolean onSubscriberReset() throws CheckedApiException {
    if (!enableReset) {
      return false;
    }

    // Handle an out-of-band seek notification from the server. There must be no pending commits
    // after this function returns.
    try (CloseableMonitor.Hold h = monitor.enter()) {
      // Discard undelivered messages.
      pullMessages();
      // Prevent further auto-commits until post-seek messages are received.
      needsCommitting = false;
    }
    committer.waitUntilEmpty();
    return true;
  }
}
