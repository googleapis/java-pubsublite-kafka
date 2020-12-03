/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pubsublite;

// [START pubsublite_kafka_producer]
import com.google.cloud.pubsublite.CloudRegion;
import com.google.cloud.pubsublite.CloudZone;
import com.google.cloud.pubsublite.ProjectNumber;
import com.google.cloud.pubsublite.TopicName;
import com.google.cloud.pubsublite.TopicPath;
import com.google.cloud.pubsublite.kafka.ProducerSettings;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

public class ProducerExample {

  public static void main(String... args) throws Exception {
    // TODO(developer): Replace these variables before running the sample.
    String cloudRegion = "your-cloud-region";
    char zoneId = 'b';
    // Use an existing Pub/Sub Lite topic.
    String topicId = "your-topic-id";
    long projectNumber = Long.parseLong("123456789");

    producerExample(cloudRegion, zoneId, projectNumber, topicId);
  }

  public static void producerExample(
      String cloudRegion, char zoneId, long projectNumber, String topicId)
      throws InterruptedException, ExecutionException {
    TopicPath topicPath =
        TopicPath.newBuilder()
            .setLocation(CloudZone.of(CloudRegion.of(cloudRegion), zoneId))
            .setProject(ProjectNumber.of(projectNumber))
            .setName(TopicName.of(topicId))
            .build();

    ProducerSettings producerSettings =
        ProducerSettings.newBuilder().setTopicPath(topicPath).build();

    for (long i = 0L; i < 10L; i++) {
      String key = "demo";
      try (Producer<byte[], byte[]> producer = producerSettings.instantiate()) {
        Future<RecordMetadata> sent =
            producer.send(
                new ProducerRecord(
                    topicPath.toString(), key.getBytes(), ("message-" + i).getBytes()));
        RecordMetadata meta = sent.get();
        System.out.println(meta.offset());
      }
    }
    System.out.printf("Published 10 messages to %s%n", topicPath.toString());
  }
}
// [END pubsublite_kafka_producer]
