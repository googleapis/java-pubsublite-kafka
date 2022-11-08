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

import com.google.cloud.pubsublite.AdminClient;
import com.google.cloud.pubsublite.Partition;
import com.google.cloud.pubsublite.TopicPath;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.common.PartitionInfo;

/** Shared behavior for producer and consumer. */
final class SharedBehavior implements AutoCloseable {
  private final AdminClient client;

  SharedBehavior(AdminClient client) {
    this.client = client;
  }

  private static PartitionInfo toPartitionInfo(TopicPath topic, Partition partition) {
    return new PartitionInfo(
        topic.toString(),
        (int) partition.value(),
        PubsubLiteNode.NODE,
        PubsubLiteNode.NODES,
        PubsubLiteNode.NODES);
  }

  List<PartitionInfo> partitionsFor(TopicPath topic, Duration timeout) {
    try {
      long partitionCount =
          client.getTopicPartitionCount(topic).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      ImmutableList.Builder<PartitionInfo> result = ImmutableList.builder();
      for (int i = 0; i < partitionCount; ++i) {
        result.add(toPartitionInfo(topic, Partition.of(i)));
      }
      return result.build();
    } catch (Throwable t) {
      throw toKafka(t);
    }
  }

  @Override
  public void close() {
    client.close();
  }
}
