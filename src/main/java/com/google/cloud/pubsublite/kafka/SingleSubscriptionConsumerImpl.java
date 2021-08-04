/*
 * Copyright 2020 Google LLC
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

import static com.google.cloud.pubsublite.kafka.KafkaExceptionUtils.toKafka;
import static com.google.common.base.Preconditions.checkState;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.core.SettableApiFuture;
import com.google.cloud.pubsublite.Offset;
import com.google.cloud.pubsublite.Partition;
import com.google.cloud.pubsublite.SequencedMessage;
import com.google.cloud.pubsublite.TopicPath;
import com.google.cloud.pubsublite.internal.BlockingPullSubscriber;
import com.google.cloud.pubsublite.internal.CloseableMonitor;
import com.google.cloud.pubsublite.internal.ExtractStatus;
import com.google.cloud.pubsublite.internal.wire.Committer;
import com.google.cloud.pubsublite.proto.SeekRequest;
import com.google.cloud.pubsublite.proto.SeekRequest.NamedTarget;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.flogger.GoogleLogger;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;

class SingleSubscriptionConsumerImpl implements SingleSubscriptionConsumer {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private final TopicPath topic;
  private final boolean autocommit;

  private final PullSubscriberFactory subscriberFactory;
  private final CommitterFactory committerFactory;

  private final CloseableMonitor monitor = new CloseableMonitor();

  static class SubscriberState {
    BlockingPullSubscriber subscriber;
    Committer committer;
    boolean needsCommitting = false;
    Optional<Offset> lastReceived = Optional.empty();
  }

  @GuardedBy("monitor.monitor")
  private final Map<Partition, SubscriberState> partitions = new HashMap<>();
  // When the set of assignments changes, this future will be set and swapped with a new future to
  // let ongoing pollers know that they should pick up new assignments.
  @GuardedBy("monitor.monitor")
  private SettableApiFuture<Void> assignmentChanged = SettableApiFuture.create();

  // Set when wakeup() has been called once.
  private final SettableApiFuture<Void> wakeupTriggered = SettableApiFuture.create();

  SingleSubscriptionConsumerImpl(
      TopicPath topic,
      boolean autocommit,
      PullSubscriberFactory subscriberFactory,
      CommitterFactory committerFactory) {
    this.topic = topic;
    this.autocommit = autocommit;
    this.subscriberFactory = subscriberFactory;
    this.committerFactory = committerFactory;
  }

  @Override
  @SuppressWarnings("GuardedBy")
  public void setAssignment(Set<Partition> assignment) {
    try (CloseableMonitor.Hold h = monitor.enter()) {

      List<SubscriberState> unassigned =
          ImmutableSet.copyOf(partitions.keySet()).stream()
              .filter(p -> !assignment.contains(p))
              .map(partitions::remove)
              .collect(Collectors.toList());
      for (SubscriberState state : unassigned) {
        state.subscriber.close();
        state.committer.stopAsync().awaitTerminated();
      }
      assignment.stream()
          .filter(p -> !partitions.containsKey(p))
          .forEach(
              ExtractStatus.rethrowAsRuntime(
                  partition -> {
                    SubscriberState s = new SubscriberState();
                    s.subscriber =
                        subscriberFactory.newPullSubscriber(
                            partition,
                            SeekRequest.newBuilder()
                                .setNamedTarget(NamedTarget.COMMITTED_CURSOR)
                                .build());
                    s.committer = committerFactory.newCommitter(partition);
                    s.committer.startAsync().awaitRunning();
                    partitions.put(partition, s);
                  }));
      assignmentChanged.set(null);
      assignmentChanged = SettableApiFuture.create();
    } catch (Throwable t) {
      throw ExtractStatus.toCanonical(t).underlying;
    }
  }

  @Override
  public Set<Partition> assignment() {
    try (CloseableMonitor.Hold h = monitor.enter()) {
      return partitions.keySet();
    }
  }

  @GuardedBy("monitor.monitor")
  private Map<Partition, Queue<SequencedMessage>> fetchAll() {
    Map<Partition, Queue<SequencedMessage>> partitionQueues = new HashMap<>();
    partitions.forEach(
        ExtractStatus.rethrowAsRuntime(
            (partition, state) -> {
              ArrayDeque<SequencedMessage> messages = new ArrayDeque<>();
              for (Optional<SequencedMessage> message = state.subscriber.messageIfAvailable();
                  message.isPresent();
                  message = state.subscriber.messageIfAvailable()) {
                messages.add(message.get());
              }
              partitionQueues.put(partition, messages);
            }));
    return partitionQueues;
  }

  private Map<Partition, Queue<SequencedMessage>> doPoll(Duration duration) {
    try {
      ImmutableList.Builder<ApiFuture<Void>> stopSleepingSignals = ImmutableList.builder();
      try (CloseableMonitor.Hold h = monitor.enter()) {
        stopSleepingSignals.add(wakeupTriggered);
        stopSleepingSignals.add(assignmentChanged);
        partitions.values().forEach(state -> stopSleepingSignals.add(state.subscriber.onData()));
      }
      try {
        ApiFuturesExtensions.whenFirstDone(stopSleepingSignals.build())
            .get(duration.toMillis(), MILLISECONDS);
      } catch (TimeoutException e) {
        return ImmutableMap.of();
      }
      try (CloseableMonitor.Hold h = monitor.enter()) {
        if (wakeupTriggered.isDone()) throw new WakeupException();
        return fetchAll();
      }
    } catch (Throwable t) {
      throw toKafka(t);
    }
  }

  @Override
  public ConsumerRecords<byte[], byte[]> poll(Duration duration) {
    if (autocommit) {
      ApiFuture<?> future = commitAll();
      ApiFutures.addCallback(
          future,
          new ApiFutureCallback<Object>() {
            @Override
            public void onFailure(Throwable throwable) {
              logger.atWarning().withCause(throwable).log("Failed to commit offsets.");
            }

            @Override
            public void onSuccess(Object result) {}
          },
          MoreExecutors.directExecutor());
    }
    Map<Partition, Queue<SequencedMessage>> partitionQueues = doPoll(duration);
    Map<TopicPartition, List<ConsumerRecord<byte[], byte[]>>> records = new HashMap<>();
    partitionQueues.forEach(
        (partition, queue) -> {
          if (queue.isEmpty()) return;
          try (CloseableMonitor.Hold h = monitor.enter()) {
            SubscriberState state = partitions.getOrDefault(partition, null);
            if (state == null) return;
            state.lastReceived = Optional.of(Iterables.getLast(queue).offset());
            state.needsCommitting = true;
          }
          List<ConsumerRecord<byte[], byte[]>> partitionRecords =
              queue.stream()
                  .map(message -> RecordTransforms.fromMessage(message, topic, partition))
                  .collect(Collectors.toList());
          records.put(
              new TopicPartition(topic.toString(), (int) partition.value()), partitionRecords);
        });
    return new ConsumerRecords<>(records);
  }

  @Override
  public ApiFuture<Map<Partition, Offset>> commitAll() {
    try (CloseableMonitor.Hold h = monitor.enter()) {
      ImmutableMap.Builder<Partition, Offset> builder = ImmutableMap.builder();
      ImmutableList.Builder<ApiFuture<?>> commitFutures = ImmutableList.builder();
      partitions.forEach(
          (partition, state) -> {
            if (!state.needsCommitting) return;
            checkState(state.lastReceived.isPresent());
            state.needsCommitting = false;
            // The Pub/Sub Lite commit offset is one more than the last received.
            Offset toCommit = Offset.of(state.lastReceived.get().value() + 1);
            builder.put(partition, toCommit);
            commitFutures.add(state.committer.commitOffset(toCommit));
          });
      Map<Partition, Offset> map = builder.build();
      return ApiFutures.transform(
          ApiFutures.allAsList(commitFutures.build()),
          ignored -> map,
          MoreExecutors.directExecutor());
    }
  }

  @Override
  @SuppressWarnings("GuardedBy")
  public ApiFuture<Void> commit(Map<Partition, Offset> commitOffsets) {
    try (CloseableMonitor.Hold h = monitor.enter()) {
      ImmutableList.Builder<ApiFuture<?>> commitFutures = ImmutableList.builder();
      commitOffsets.forEach(
          (partition, offset) -> {
            if (!partitions.containsKey(partition)) {
              throw new CommitFailedException(
                  "Tried to commit to partition "
                      + partition.value()
                      + " which is not assigned to this consumer.");
            }
            commitFutures.add(partitions.get(partition).committer.commitOffset(offset));
          });
      return ApiFutures.transform(
          ApiFutures.allAsList(commitFutures.build()),
          ignored -> null,
          MoreExecutors.directExecutor());
    }
  }

  @Override
  public void doSeek(Partition partition, SeekRequest request) throws KafkaException {
    try (CloseableMonitor.Hold h = monitor.enter()) {
      if (!partitions.containsKey(partition)) {
        throw new IllegalStateException(
            "Received seek for partition "
                + partition.value()
                + " which is not assigned to this consumer.");
      }
      SubscriberState state = partitions.get(partition);
      state.subscriber.close();
      state.subscriber = subscriberFactory.newPullSubscriber(partition, request);
    } catch (IllegalStateException e) {
      throw e;
    } catch (Throwable t) {
      throw toKafka(t);
    }
  }

  @Override
  public Optional<Long> position(Partition partition) {
    try (CloseableMonitor.Hold h = monitor.enter()) {
      if (!partitions.containsKey(partition)) return Optional.empty();
      return partitions.get(partition).lastReceived.map(lastReceived -> lastReceived.value() + 1);
    }
  }

  @Override
  public void close(Duration duration) {
    try (CloseableMonitor.Hold h = monitor.enter()) {
      for (SubscriberState state : partitions.values()) {
        state.subscriber.close();
        state.committer.stopAsync().awaitTerminated();
      }
    } catch (Throwable t) {
      throw toKafka(t);
    }
  }

  @Override
  public void wakeup() {
    wakeupTriggered.set(null);
  }
}
