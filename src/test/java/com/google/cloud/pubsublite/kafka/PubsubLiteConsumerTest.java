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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.api.core.ApiFutures;
import com.google.api.core.SettableApiFuture;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.cloud.pubsublite.AdminClient;
import com.google.cloud.pubsublite.CloudZone;
import com.google.cloud.pubsublite.Offset;
import com.google.cloud.pubsublite.Partition;
import com.google.cloud.pubsublite.ProjectNumber;
import com.google.cloud.pubsublite.SubscriptionPath;
import com.google.cloud.pubsublite.TopicName;
import com.google.cloud.pubsublite.TopicPath;
import com.google.cloud.pubsublite.internal.CheckedApiException;
import com.google.cloud.pubsublite.internal.CursorClient;
import com.google.cloud.pubsublite.internal.TopicStatsClient;
import com.google.cloud.pubsublite.internal.testing.FakeApiService;
import com.google.cloud.pubsublite.internal.testing.UnitTestExamples;
import com.google.cloud.pubsublite.internal.wire.Assigner;
import com.google.cloud.pubsublite.internal.wire.AssignerFactory;
import com.google.cloud.pubsublite.internal.wire.PartitionAssignmentReceiver;
import com.google.cloud.pubsublite.proto.Cursor;
import com.google.cloud.pubsublite.proto.SeekRequest;
import com.google.cloud.pubsublite.proto.SeekRequest.NamedTarget;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;
import com.google.common.reflect.ImmutableTypeToInstanceMap;
import com.google.protobuf.util.Timestamps;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.clients.consumer.OffsetCommitCallback;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.BrokerNotAvailableException;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Spy;

@RunWith(JUnit4.class)
public class PubsubLiteConsumerTest {
  private static Duration INFINITE_DURATION = Duration.ofMillis(Long.MAX_VALUE);

  private static TopicPartition exampleTopicPartition() {
    return new TopicPartition(
        UnitTestExamples.example(TopicPath.class).toString(),
        (int) UnitTestExamples.example(Partition.class).value());
  }

  private static OffsetAndMetadata exampleOffsetAndMetadata() {
    return new OffsetAndMetadata(UnitTestExamples.example(Offset.class).value());
  }

  private static <T> T example(Class<T> klass) {
    ImmutableTypeToInstanceMap<Object> map =
        ImmutableTypeToInstanceMap.builder()
            .put(TopicPartition.class, exampleTopicPartition())
            .put(OffsetAndMetadata.class, exampleOffsetAndMetadata())
            .build();
    T instance = (T) map.getInstance(klass);
    if (instance != null) return instance;
    return UnitTestExamples.example(klass);
  }

  @Mock ConsumerFactory consumerFactory;
  @Mock AssignerFactory assignerFactory;
  @Mock CursorClient cursorClient;
  @Mock AdminClient adminClient;
  @Mock TopicStatsClient topicStatsClient;

  abstract static class FakeAssigner extends FakeApiService implements Assigner {}

  @Spy FakeAssigner assigner;
  @Mock SingleSubscriptionConsumer underlying;

  Consumer<byte[], byte[]> consumer;

  @Before
  public void setUp() {
    initMocks(this);
    consumer =
        new PubsubLiteConsumer(
            example(SubscriptionPath.class),
            example(TopicPath.class),
            new SharedBehavior(adminClient),
            consumerFactory,
            assignerFactory,
            cursorClient,
            topicStatsClient);
    when(consumerFactory.newConsumer()).thenReturn(underlying);
  }

  @Test
  public void unsupportedOperations() {
    assertThrows(
        UnsupportedOperationException.class, () -> consumer.subscribe(Pattern.compile(".*")));
    assertThrows(
        UnsupportedOperationException.class,
        () -> consumer.subscribe(Pattern.compile(".*"), mock(ConsumerRebalanceListener.class)));
    assertThrows(
        UnsupportedOperationException.class, () -> consumer.subscribe(ImmutableList.of("a", "b")));
    assertThrows(
        UnsupportedOperationException.class,
        () ->
            consumer.subscribe(ImmutableList.of("a", "b"), mock(ConsumerRebalanceListener.class)));
  }

  @Test
  public void staticOperations() {
    // Pre-subscribe: returns empty set.
    assertThat(consumer.subscription()).isEmpty();

    assertThat(consumer.metrics()).isEmpty();
    assertThat(consumer.groupMetadata().groupId())
        .isEqualTo(example(SubscriptionPath.class).toString());
    TopicPartition other = new TopicPartition(example(TopicPath.class).toString(), 2);
    assertThat(consumer.beginningOffsets(ImmutableList.of(example(TopicPartition.class), other)))
        .containsExactly(example(TopicPartition.class), 0L, other, 0L);
    // No-op operations.
    assertThat(consumer.paused()).isEmpty();
    consumer.pause(ImmutableList.of());
    consumer.resume(ImmutableList.of());
  }

  @Test
  public void badTopicOperations() throws Exception {
    TopicPath badTopic =
        TopicPath.newBuilder()
            .setLocation(example(CloudZone.class))
            .setProject(example(ProjectNumber.class))
            .setName(TopicName.of("abc"))
            .build();
    assertThrows(
        UnsupportedOperationException.class,
        () -> consumer.subscribe(ImmutableList.of(badTopic.toString())));
    assertThrows(
        UnsupportedOperationException.class,
        () ->
            consumer.subscribe(
                ImmutableList.of(badTopic.toString()), mock(ConsumerRebalanceListener.class)));

    TopicPartition bad = new TopicPartition(badTopic.toString(), 4);
    assertThrows(UnsupportedOperationException.class, () -> consumer.assign(ImmutableList.of(bad)));
    assertThrows(UnsupportedOperationException.class, () -> consumer.seek(bad, 3));
    assertThrows(
        UnsupportedOperationException.class, () -> consumer.seek(bad, new OffsetAndMetadata(3)));
    assertThrows(UnsupportedOperationException.class, () -> consumer.position(bad));
    assertThrows(UnsupportedOperationException.class, () -> consumer.position(bad, Duration.ZERO));
    assertThrows(UnsupportedOperationException.class, () -> consumer.committed(bad));
    assertThrows(UnsupportedOperationException.class, () -> consumer.committed(bad, Duration.ZERO));
    assertThrows(
        UnsupportedOperationException.class,
        () -> consumer.committed(ImmutableSet.of(example(TopicPartition.class), bad)));
    assertThrows(
        UnsupportedOperationException.class,
        () ->
            consumer.committed(ImmutableSet.of(example(TopicPartition.class), bad), Duration.ZERO));
    assertThrows(
        UnsupportedOperationException.class, () -> consumer.partitionsFor(badTopic.toString()));
    assertThrows(
        UnsupportedOperationException.class,
        () -> consumer.partitionsFor(badTopic.toString(), Duration.ZERO));
    assertThrows(
        UnsupportedOperationException.class,
        () -> consumer.beginningOffsets(ImmutableList.of(example(TopicPartition.class), bad)));
    assertThrows(
        UnsupportedOperationException.class,
        () ->
            consumer.beginningOffsets(
                ImmutableList.of(example(TopicPartition.class), bad), Duration.ZERO));

    // Only valid if subscribed.
    consumer.assign(ImmutableList.of(example(TopicPartition.class)));
    assertThrows(
        UnsupportedOperationException.class,
        () -> consumer.commitSync(ImmutableMap.of(bad, new OffsetAndMetadata(3))));
    assertThrows(
        UnsupportedOperationException.class,
        () -> consumer.commitSync(ImmutableMap.of(bad, new OffsetAndMetadata(3)), Duration.ZERO));
    assertThrows(
        UnsupportedOperationException.class,
        () -> consumer.commitAsync(ImmutableMap.of(bad, new OffsetAndMetadata(3)), null));
    assertThrows(
        UnsupportedOperationException.class, () -> consumer.seekToBeginning(ImmutableList.of(bad)));
    assertThrows(
        UnsupportedOperationException.class, () -> consumer.seekToEnd(ImmutableList.of(bad)));
  }

  @Test
  public void invalidBeforeSubscribeOperations() {
    assertThrows(IllegalStateException.class, () -> consumer.assignment());
    assertThrows(IllegalStateException.class, () -> consumer.poll(3));
    assertThrows(IllegalStateException.class, () -> consumer.poll(Duration.ZERO));
    assertThrows(IllegalStateException.class, () -> consumer.commitSync(ImmutableMap.of()));
    assertThrows(
        IllegalStateException.class, () -> consumer.commitSync(ImmutableMap.of(), Duration.ZERO));
    assertThrows(
        IllegalStateException.class,
        () -> consumer.commitAsync(ImmutableMap.of(), mock(OffsetCommitCallback.class)));
    assertThrows(IllegalStateException.class, () -> consumer.commitSync());
    assertThrows(IllegalStateException.class, () -> consumer.commitSync(Duration.ZERO));
    assertThrows(IllegalStateException.class, () -> consumer.commitAsync());
    assertThrows(
        IllegalStateException.class, () -> consumer.commitAsync(mock(OffsetCommitCallback.class)));
    assertThrows(
        IllegalStateException.class, () -> consumer.seek(example(TopicPartition.class), 3));
    assertThrows(
        IllegalStateException.class,
        () -> consumer.seek(example(TopicPartition.class), new OffsetAndMetadata(3)));
    assertThrows(
        IllegalStateException.class,
        () -> consumer.seekToBeginning(ImmutableList.of(example(TopicPartition.class))));
    assertThrows(
        IllegalStateException.class,
        () -> consumer.seekToEnd(ImmutableList.of(example(TopicPartition.class))));
    assertThrows(
        IllegalStateException.class, () -> consumer.position(example(TopicPartition.class)));
    assertThrows(
        IllegalStateException.class,
        () -> consumer.position(example(TopicPartition.class), Duration.ZERO));
    assertThrows(IllegalStateException.class, () -> consumer.wakeup());
  }

  @Test
  public void validAssign() throws Exception {
    consumer.assign(ImmutableList.of(example(TopicPartition.class)));
    verify(consumerFactory, times(1)).newConsumer();
    verify(underlying, times(1)).setAssignment(ImmutableSet.of(example(Partition.class)));
    consumer.assign(ImmutableList.of(example(TopicPartition.class)));
    verify(consumerFactory, times(1)).newConsumer();
    verify(underlying, times(2)).setAssignment(ImmutableSet.of(example(Partition.class)));
    verify(assignerFactory, times(0)).New(any());
    consumer.unsubscribe();
    verify(underlying).close(INFINITE_DURATION);
  }

  @Test
  public void simpleConsumerMethods() {
    consumer.assign(ImmutableList.of(example(TopicPartition.class)));
    when(underlying.assignment()).thenReturn(ImmutableSet.of(example(Partition.class)));
    assertThat(consumer.assignment()).containsExactly(example(TopicPartition.class));
    assertThat(consumer.subscription()).containsExactly(example(TopicPath.class).toString());
    consumer.wakeup();
    verify(underlying).wakeup();

    // Assign empty calls unsubscribe.
    consumer.assign(ImmutableSet.of());
    verify(underlying).close(INFINITE_DURATION);
  }

  @Test
  public void validSubscribe() throws Exception {
    ConsumerRebalanceListener listener = mock(ConsumerRebalanceListener.class);
    AtomicReference<PartitionAssignmentReceiver> receiver = new AtomicReference<>(null);
    doAnswer(
            args -> {
              receiver.set(args.getArgument(0));
              return assigner;
            })
        .when(assignerFactory)
        .New(any());
    consumer.subscribe(ImmutableList.of(example(TopicPath.class).toString()), listener);
    verify(consumerFactory).newConsumer();
    verify(assignerFactory).New(any());
    verify(assigner).startAsync();
    verify(assigner).awaitRunning();
    receiver.get().handleAssignment(ImmutableSet.of(Partition.of(5)));
    verify(listener)
        .onPartitionsAssigned(
            ImmutableSet.of(new TopicPartition(example(TopicPath.class).toString(), 5)));
    verify(underlying).setAssignment(ImmutableSet.of(Partition.of(5)));
    // Duplicate subscribe does nothing.
    receiver.get().handleAssignment(ImmutableSet.of(Partition.of(5)));
    verifyNoMoreInteractions(listener);
    // Add and remove.
    receiver.get().handleAssignment(ImmutableSet.of(Partition.of(7)));
    verify(listener)
        .onPartitionsLost(
            ImmutableSet.of(new TopicPartition(example(TopicPath.class).toString(), 5)));
    verify(listener)
        .onPartitionsAssigned(
            ImmutableSet.of(new TopicPartition(example(TopicPath.class).toString(), 7)));
    verify(underlying).setAssignment(ImmutableSet.of(Partition.of(7)));
  }

  @Test
  public void polling() {
    consumer.assign(ImmutableList.of(example(TopicPartition.class)));

    when(underlying.poll(Duration.ofMillis(1)))
        .thenReturn(
            new ConsumerRecords<>(
                Multimaps.asMap(
                    ImmutableListMultimap.of(
                        example(TopicPartition.class),
                        new ConsumerRecord<>(
                            example(TopicPath.class).toString(),
                            0,
                            0,
                            new byte[0],
                            new byte[0])))));
    ConsumerRecords<byte[], byte[]> records = consumer.poll(1);
    assertThat(records.count()).isEqualTo(1);
    ConsumerRecord<byte[], byte[]> record = records.iterator().next();
    assertThat(record.topic()).isEqualTo(example(TopicPath.class).toString());
    assertThat(record.partition()).isEqualTo(0);

    when(underlying.poll(Duration.ofMillis(2)))
        .thenReturn(
            new ConsumerRecords<>(
                Multimaps.asMap(
                    ImmutableListMultimap.of(
                        example(TopicPartition.class),
                        new ConsumerRecord<>(
                            example(TopicPath.class).toString(),
                            1,
                            0,
                            new byte[0],
                            new byte[0])))));
    records = consumer.poll(Duration.ofMillis(2));
    assertThat(records.count()).isEqualTo(1);
    record = records.iterator().next();
    assertThat(record.topic()).isEqualTo(example(TopicPath.class).toString());
    assertThat(record.partition()).isEqualTo(1);

    when(underlying.poll(Duration.ofMillis(3)))
        .thenAnswer(
            args -> {
              throw new KafkaException();
            });
    assertThrows(KafkaException.class, () -> consumer.poll(Duration.ofMillis(3)));
  }

  @Test
  public void commitSync() {
    consumer.assign(ImmutableList.of(example(TopicPartition.class)));
    when(underlying.commit(ImmutableMap.of(example(Partition.class), example(Offset.class))))
        .thenReturn(ApiFutures.immediateFuture(null));
    consumer.commitSync(
        ImmutableMap.of(example(TopicPartition.class), example(OffsetAndMetadata.class)));
    verify(underlying).commit(ImmutableMap.of(example(Partition.class), example(Offset.class)));
    when(underlying.commit(ImmutableMap.of(example(Partition.class), example(Offset.class))))
        .thenReturn(SettableApiFuture.create());
    assertThrows(
        TimeoutException.class,
        () ->
            consumer.commitSync(
                ImmutableMap.of(example(TopicPartition.class), example(OffsetAndMetadata.class)),
                Duration.ZERO));

    when(underlying.commitAll()).thenReturn(ApiFutures.immediateFuture(null));
    consumer.commitSync();
    verify(underlying).commitAll();
    when(underlying.commitAll()).thenReturn(SettableApiFuture.create());
    assertThrows(TimeoutException.class, () -> consumer.commitSync(Duration.ZERO));
  }

  @Test
  public void commitAsync() {
    consumer.assign(ImmutableList.of(example(TopicPartition.class)));
    OffsetCommitCallback callback = mock(OffsetCommitCallback.class);
    when(underlying.commit(ImmutableMap.of(example(Partition.class), example(Offset.class))))
        .thenReturn(ApiFutures.immediateFuture(null));
    consumer.commitAsync(
        ImmutableMap.of(example(TopicPartition.class), example(OffsetAndMetadata.class)), callback);
    verify(callback)
        .onComplete(
            ImmutableMap.of(example(TopicPartition.class), example(OffsetAndMetadata.class)), null);

    callback = mock(OffsetCommitCallback.class);
    when(underlying.commit(ImmutableMap.of(example(Partition.class), example(Offset.class))))
        .thenReturn(ApiFutures.immediateFailedFuture(new KafkaException()));
    consumer.commitAsync(
        ImmutableMap.of(example(TopicPartition.class), example(OffsetAndMetadata.class)), callback);
    verify(callback).onComplete(isNull(), any(KafkaException.class));

    when(underlying.commitAll())
        .thenReturn(
            ApiFutures.immediateFuture(
                ImmutableMap.of(example(Partition.class), example(Offset.class))));
    consumer.commitAsync();
    verify(underlying, times(1)).commitAll();

    callback = mock(OffsetCommitCallback.class);
    consumer.commitAsync(callback);
    verify(underlying, times(2)).commitAll();
    verify(callback)
        .onComplete(
            ImmutableMap.of(example(TopicPartition.class), example(OffsetAndMetadata.class)), null);

    callback = mock(OffsetCommitCallback.class);
    when(underlying.commitAll()).thenReturn(ApiFutures.immediateFailedFuture(new KafkaException()));
    consumer.commitAsync(callback);
    verify(underlying, times(3)).commitAll();
    verify(callback).onComplete(isNull(), any(KafkaException.class));
  }

  @Test
  public void seek() {
    consumer.assign(ImmutableList.of(example(TopicPartition.class)));
    consumer.seek(example(TopicPartition.class), 1);
    verify(underlying)
        .doSeek(
            example(Partition.class),
            SeekRequest.newBuilder().setCursor(Cursor.newBuilder().setOffset(1)).build());
    consumer.seek(example(TopicPartition.class), new OffsetAndMetadata(2));
    verify(underlying)
        .doSeek(
            example(Partition.class),
            SeekRequest.newBuilder().setCursor(Cursor.newBuilder().setOffset(2)).build());
    consumer.seekToBeginning(ImmutableList.of(example(TopicPartition.class)));
    verify(underlying)
        .doSeek(
            example(Partition.class),
            SeekRequest.newBuilder().setCursor(Cursor.newBuilder().setOffset(0)).build());
    consumer.seekToEnd(ImmutableList.of(example(TopicPartition.class)));
    verify(underlying)
        .doSeek(
            example(Partition.class),
            SeekRequest.newBuilder().setNamedTarget(NamedTarget.HEAD).build());
  }

  @Test
  public void partitionsFor() {
    when(adminClient.getTopicPartitionCount(example(TopicPath.class)))
        .thenReturn(ApiFutures.immediateFuture(2L));
    List<PartitionInfo> info = consumer.partitionsFor(example(TopicPath.class).toString());
    assertThat(info.size()).isEqualTo(2L);
  }

  @Test
  public void endOffsets() {
    TopicPartition partition2 = new TopicPartition(example(TopicPath.class).toString(), 2);
    TopicPartition partition4 = new TopicPartition(example(TopicPath.class).toString(), 4);
    when(topicStatsClient.computeHeadCursor(example(TopicPath.class), Partition.of(2)))
        .thenReturn(ApiFutures.immediateFuture(Cursor.newBuilder().setOffset(22).build()));
    when(topicStatsClient.computeHeadCursor(example(TopicPath.class), Partition.of(4)))
        .thenReturn(ApiFutures.immediateFuture(Cursor.newBuilder().setOffset(44).build()));
    Map<TopicPartition, Long> output =
        consumer.endOffsets(ImmutableList.of(partition2, partition4));
    assertThat(output).isEqualTo(ImmutableMap.of(partition2, 22L, partition4, 44L));
  }

  @Test
  public void offsetsForTimes() {
    TopicPartition partition2 = new TopicPartition(example(TopicPath.class).toString(), 2);
    TopicPartition partition4 = new TopicPartition(example(TopicPath.class).toString(), 4);
    when(topicStatsClient.computeCursorForEventTime(
            example(TopicPath.class), Partition.of(2), Timestamps.fromMillis(2000)))
        .thenReturn(
            ApiFutures.immediateFuture(Optional.of(Cursor.newBuilder().setOffset(22).build())));
    when(topicStatsClient.computeCursorForEventTime(
            example(TopicPath.class), Partition.of(4), Timestamps.fromMillis(4000)))
        .thenReturn(ApiFutures.immediateFuture(Optional.empty()));
    Map<TopicPartition, OffsetAndTimestamp> output =
        consumer.offsetsForTimes(ImmutableMap.of(partition2, 2000L, partition4, 4000L));

    Map<TopicPartition, OffsetAndTimestamp> expected = new HashMap<>();
    expected.put(partition2, new OffsetAndTimestamp(22, 2000));
    expected.put(partition4, null);
    assertThat(output).isEqualTo(expected);
  }

  @Test
  public void offsetsForTimesFailure() {
    TopicPartition partition2 = new TopicPartition(example(TopicPath.class).toString(), 2);
    TopicPartition partition4 = new TopicPartition(example(TopicPath.class).toString(), 4);
    when(topicStatsClient.computeCursorForEventTime(
            example(TopicPath.class), Partition.of(2), Timestamps.fromMillis(2000)))
        .thenReturn(
            ApiFutures.immediateFuture(Optional.of(Cursor.newBuilder().setOffset(22).build())));
    when(topicStatsClient.computeCursorForEventTime(
            example(TopicPath.class), Partition.of(4), Timestamps.fromMillis(4000)))
        .thenReturn(
            ApiFutures.immediateFailedFuture(new CheckedApiException(Code.UNAVAILABLE).underlying));

    assertThrows(
        BrokerNotAvailableException.class,
        () -> consumer.offsetsForTimes(ImmutableMap.of(partition2, 2000L, partition4, 4000L)));
  }

  @Test
  public void close() {
    consumer.close();
    verify(adminClient).close();
    verify(cursorClient).close();
    verify(topicStatsClient).close();
  }
}
