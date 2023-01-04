/*
 * Copyright 2022 Google LLC
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

// [START pubsublite_kafka_native_producer]
import com.google.cloud.pubsublite.CloudRegion;
import com.google.cloud.pubsublite.CloudZone;
import com.google.cloud.pubsublite.ProjectNumber;
import com.google.cloud.pubsublite.TopicName;
import com.google.cloud.pubsublite.TopicPath;
import com.google.cloud.pubsublite.kafka.ClientParameters;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.ByteArraySerializer;

public class KafkaProducerExample {

  public static void main(String... args) throws Exception {
    // TODO(developer): Replace these variables before running the sample.
    String cloudRegion = "your-cloud-region";
    char zoneId = 'b';
    // Use an existing Pub/Sub Lite topic.
    String topicId = "your-topic-id";
    // Using the project number is required for constructing a Pub/Sub Lite
    // topic path that the Kafka producer can use.
    long projectNumber = Long.parseLong("123456789");

    kafkaProducerExample(cloudRegion, zoneId, projectNumber, topicId);
  }

  private static void recursivePrintStack(Throwable e) {
    e.printStackTrace(System.err);
    for (Throwable t : e.getSuppressed()) {
      recursivePrintStack(t);
    }
  }

  public static void kafkaProducerExample(
      String cloudRegion, char zoneId, long projectNumber, String topicId)
      throws InterruptedException, ExecutionException {
    ProjectNumber project = ProjectNumber.of(projectNumber);
    CloudZone location = CloudZone.of(CloudRegion.of(cloudRegion), zoneId);
    TopicPath topicPath =
        TopicPath.newBuilder()
            .setProject(project)
            .setLocation(location)
            .setName(TopicName.of(topicId))
            .build();

    ImmutableMap.Builder<String, Object> properties = ImmutableMap.builder();
    properties.putAll(ClientParameters.getProducerParams(project, location.region()));

    List<Future<RecordMetadata>> futures = new ArrayList<>();
    try (KafkaProducer<byte[], byte[]> producer = new KafkaProducer<>(properties.build(),
        new ByteArraySerializer(), new ByteArraySerializer())) {
      for (long i = 0L; i < 10L; i++) {
        String key = "demo";
        Future<RecordMetadata> future =
            producer.send(
                new ProducerRecord<>(
                    topicPath.toString(), key.getBytes(), ("message-" + i).getBytes()));
        futures.add(future);
      }
      for (Future<RecordMetadata> future : futures) {
        RecordMetadata meta = future.get();
        System.out.println(meta.offset());
      }
    } catch (Throwable t) {
      recursivePrintStack(t);
    }
    System.out.printf("Published 10 messages to %s%n", topicPath);
  }
}
// [END pubsublite_kafka_native_producer]
