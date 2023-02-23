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

import com.google.cloud.pubsublite.Partition;
import com.google.cloud.pubsublite.TopicPath;
import com.google.cloud.pubsublite.proto.AttributeValues;
import com.google.cloud.pubsublite.proto.PubSubMessage;
import com.google.cloud.pubsublite.proto.SequencedMessage;
import com.google.common.collect.ImmutableListMultimap;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.record.TimestampType;

class RecordTransforms {
  private RecordTransforms() {}

  static PubSubMessage toMessage(ProducerRecord<byte[], byte[]> record) {
    PubSubMessage.Builder builder =
        PubSubMessage.newBuilder()
            .setKey(ByteString.copyFrom(record.key()))
            .setData(ByteString.copyFrom(record.value()));
    if (record.timestamp() != null) {
      builder.setEventTime(Timestamps.fromMillis(record.timestamp()));
    }
    ImmutableListMultimap.Builder<String, ByteString> attributes = ImmutableListMultimap.builder();
    record
        .headers()
        .forEach(header -> attributes.put(header.key(), ByteString.copyFrom(header.value())));
    attributes
        .build()
        .asMap()
        .forEach(
            (key, values) ->
                builder.putAttributes(
                    key, AttributeValues.newBuilder().addAllValues(values).build()));
    return builder.build();
  }

  static ConsumerRecord<byte[], byte[]> fromMessage(
      SequencedMessage sequenced, TopicPath topic, Partition partition) {
    PubSubMessage message = sequenced.getMessage();
    Headers headers = new LiteHeaders(message.getAttributesMap());
    TimestampType type;
    Timestamp timestamp;
    if (message.hasEventTime()) {
      type = TimestampType.CREATE_TIME;
      timestamp = message.getEventTime();
    } else {
      type = TimestampType.LOG_APPEND_TIME;
      timestamp = sequenced.getPublishTime();
    }
    return new ConsumerRecord<>(
        topic.toString(),
        (int) partition.value(),
        sequenced.getCursor().getOffset(),
        Timestamps.toMillis(timestamp),
        type,
        0L,
        message.getKey().size(),
        message.getData().size(),
        message.getKey().toByteArray(),
        message.getData().toByteArray(),
        headers);
  }
}
