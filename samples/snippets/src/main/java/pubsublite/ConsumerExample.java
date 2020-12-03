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

// [START pubsublite_kafka_consumer]
import com.google.cloud.pubsublite.CloudRegion;
import com.google.cloud.pubsublite.CloudZone;
import com.google.cloud.pubsublite.ProjectNumber;
import com.google.cloud.pubsublite.SubscriptionName;
import com.google.cloud.pubsublite.SubscriptionPath;
import com.google.cloud.pubsublite.TopicName;
import com.google.cloud.pubsublite.TopicPath;
import com.google.cloud.pubsublite.cloudpubsub.FlowControlSettings;
import com.google.cloud.pubsublite.kafka.ConsumerSettings;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

public class ConsumerExample {

  public static void main(String... args) throws Exception {
    // TODO(developer): Replace these variables before running the sample.
    String cloudRegion = "your-cloud-region";
    char zoneId = 'b';
    // Use an existing Pub/Sub Lite topic and subscription.
    String topicId = "your-topic-id";
    String subscriptionId = "your-subscription-id";
    long projectNumber = Long.parseLong("123456789");

    consumerExample(cloudRegion, zoneId, projectNumber, topicId, subscriptionId);
  }

  public static void consumerExample(
      String cloudRegion, char zoneId, long projectNumber, String topicId, String subscriptionId) {

    CloudZone location = CloudZone.of(CloudRegion.of(cloudRegion), zoneId);

    TopicPath topicPath =
        TopicPath.newBuilder()
            .setLocation(location)
            .setProject(ProjectNumber.of(projectNumber))
            .setName(TopicName.of(topicId))
            .build();

    SubscriptionPath subscription =
        SubscriptionPath.newBuilder()
            .setLocation(location)
            .setProject(ProjectNumber.of(projectNumber))
            .setName(SubscriptionName.of(subscriptionId))
            .build();

    FlowControlSettings flowControlSettings =
        FlowControlSettings.builder()
            // 10 MiB. Must be greater than the allowed size of the largest message (1 MiB).
            .setBytesOutstanding(10 * 1024 * 1024L)
            // 1,000 outstanding messages. Must be >0.
            .setMessagesOutstanding(1000L)
            .build();

    ConsumerSettings settings =
        ConsumerSettings.newBuilder()
            .setSubscriptionPath(subscription)
            .setPerPartitionFlowControlSettings(flowControlSettings)
            .setAutocommit(true)
            .build();

    try (Consumer<byte[], byte[]> consumer = settings.instantiate()) {
      consumer.subscribe(Arrays.asList(topicPath.toString()));
      // It takes up to 90s for the subscriber to warm up.
      while (true) {
        ConsumerRecords<byte[], byte[]> records = consumer.poll(Duration.ofMillis(100));
        for (ConsumerRecord<byte[], byte[]> record : records) {
          long offset = record.offset();
          byte[] value = record.value();
          System.out.printf("Received %s: %s%n", offset, new String(value, StandardCharsets.UTF_8));
        }
        // Early exit. Remove entirely to keep the consumer alive indefinitely.
        if (!records.isEmpty()) {
          break;
        }
      }
    }
  }
}
// [END pubsublite_kafka_consumer]
