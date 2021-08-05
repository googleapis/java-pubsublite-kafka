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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.api.core.ApiFutures;
import com.google.cloud.pubsublite.Message;
import com.google.cloud.pubsublite.Offset;
import com.google.cloud.pubsublite.Partition;
import com.google.cloud.pubsublite.SequencedMessage;
import com.google.cloud.pubsublite.internal.BlockingPullSubscriber;
import com.google.cloud.pubsublite.internal.CheckedApiException;
import com.google.cloud.pubsublite.internal.wire.Committer;
import com.google.cloud.pubsublite.internal.wire.SubscriberResetHandler;
import com.google.cloud.pubsublite.proto.SeekRequest;
import com.google.cloud.pubsublite.proto.SeekRequest.NamedTarget;
import com.google.protobuf.Timestamp;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

@RunWith(JUnit4.class)
public class SinglePartitionSubscriberTest {
  private static final SeekRequest INITIAL_SEEK =
      SeekRequest.newBuilder().setNamedTarget(NamedTarget.COMMITTED_CURSOR).build();
  private static final Partition PARTITION = Partition.of(2);

  @Mock PullSubscriberFactory subscriberFactory;
  @Mock Committer committer;
  @Mock BlockingPullSubscriber pullSubscriber;

  @Captor private ArgumentCaptor<SubscriberResetHandler> resetHandlerCaptor;

  private SinglePartitionSubscriber subscriber;

  @Before
  public void setUp() throws CheckedApiException {
    initMocks(this);
    when(subscriberFactory.newPullSubscriber(eq(PARTITION), eq(INITIAL_SEEK), any()))
        .thenReturn(pullSubscriber);
    subscriber =
        new SinglePartitionSubscriber(subscriberFactory, PARTITION, INITIAL_SEEK, committer);
  }

  @After
  public void tearDown() throws Exception {
    verifyNoMoreInteractions(subscriberFactory);
    verifyNoMoreInteractions(pullSubscriber);
    verifyNoMoreInteractions(committer);
  }

  private static SequencedMessage message(long offset) {
    return SequencedMessage.of(
        Message.builder().build(), Timestamp.getDefaultInstance(), Offset.of(offset), 0L);
  }

  @Test
  public void pullAndCommit() throws Exception {
    verify(subscriberFactory).newPullSubscriber(eq(PARTITION), eq(INITIAL_SEEK), any());

    when(pullSubscriber.messageIfAvailable())
        .thenReturn(Optional.of(message(3)))
        .thenReturn(Optional.of(message(5)))
        .thenReturn(Optional.of(message(7)))
        .thenReturn(Optional.empty());
    assertThat(subscriber.getMessages()).containsExactly(message(3), message(5), message(7));
    assertThat(subscriber.position()).hasValue(8);
    verify(pullSubscriber, times(4)).messageIfAvailable();

    when(committer.commitOffset(Offset.of(8))).thenReturn(ApiFutures.immediateFuture(null));
    subscriber.autoCommit();
    verify(committer).commitOffset(Offset.of(8));

    // Second auto commit does nothing.
    subscriber.autoCommit();
  }

  @Test
  public void resetSubscriber() throws Exception {
    verify(subscriberFactory)
        .newPullSubscriber(eq(PARTITION), eq(INITIAL_SEEK), resetHandlerCaptor.capture());

    when(pullSubscriber.messageIfAvailable())
        .thenReturn(Optional.of(message(3)))
        .thenReturn(Optional.of(message(5)))
        .thenReturn(Optional.of(message(7)))
        .thenReturn(Optional.empty());
    assertThat(subscriber.getMessages()).containsExactly(message(3), message(5), message(7));

    when(pullSubscriber.messageIfAvailable())
        .thenReturn(Optional.of(message(9)))
        .thenReturn(Optional.empty());
    assertThat(resetHandlerCaptor.getValue().handleReset()).isTrue();
    verify(committer).waitUntilEmpty();
    verify(pullSubscriber, times(6)).messageIfAvailable();

    // Subsequent messages are discarded.
    assertThat(subscriber.position()).hasValue(8);

    // Auto commit does nothing.
    subscriber.autoCommit();
  }
}
