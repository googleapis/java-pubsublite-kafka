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

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.TestCase.assertNotNull;

import com.google.cloud.pubsublite.AdminClient;
import com.google.cloud.pubsublite.AdminClientSettings;
import com.google.cloud.pubsublite.CloudRegion;
import com.google.cloud.pubsublite.CloudZone;
import com.google.cloud.pubsublite.ProjectNumber;
import com.google.cloud.pubsublite.ReservationName;
import com.google.cloud.pubsublite.ReservationPath;
import com.google.cloud.pubsublite.SubscriptionName;
import com.google.cloud.pubsublite.SubscriptionPath;
import com.google.cloud.pubsublite.TopicName;
import com.google.cloud.pubsublite.TopicPath;
import com.google.cloud.pubsublite.proto.Subscription;
import com.google.cloud.pubsublite.proto.Subscription.DeliveryConfig;
import com.google.cloud.pubsublite.proto.Subscription.DeliveryConfig.DeliveryRequirement;
import com.google.cloud.pubsublite.proto.Topic;
import com.google.cloud.pubsublite.proto.Topic.PartitionConfig;
import com.google.cloud.pubsublite.proto.Topic.ReservationConfig;
import com.google.cloud.pubsublite.proto.Topic.RetentionConfig;
import com.google.protobuf.util.Durations;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class QuickStartIT {

  private ByteArrayOutputStream bout;
  private PrintStream out;
  private PrintStream console;
  Random rand = new Random();

  private static final ProjectNumber projectNumber =
      ProjectNumber.of(Long.parseLong(System.getenv("GOOGLE_CLOUD_PROJECT_NUMBER")));
  private final CloudRegion cloudRegion = CloudRegion.of("us-central1");
  private final char zoneId = (char) (rand.nextInt(3) + 'a');

  private final CloudZone cloudZone = CloudZone.of(cloudRegion, zoneId);
  private static final String suffix = UUID.randomUUID().toString();
  private static final String topicId = "lite-topic-" + suffix;
  private static final String subscriptionId = "lite-subscription-" + suffix;

  private static void requireEnvVar(String varName) {
    assertNotNull(
        "Environment variable " + varName + " is required to perform these tests.",
        System.getenv(varName));
  }

  @BeforeClass
  public static void checkRequirements() {
    requireEnvVar("GOOGLE_CLOUD_PROJECT_NUMBER");
  }

  @Before
  public void setUp() throws Exception {
    ReservationPath reservationPath =
        ReservationPath.newBuilder()
            .setProject(projectNumber)
            .setLocation(cloudRegion)
            .setName(ReservationName.of("java-pubsublite-kafka-reservation"))
            .build();

    TopicPath topicPath =
        TopicPath.newBuilder()
            .setProject(projectNumber)
            .setLocation(cloudZone)
            .setName(TopicName.of(topicId))
            .build();

    Topic topic =
        Topic.newBuilder()
            .setPartitionConfig(PartitionConfig.newBuilder().setCount(1))
            .setRetentionConfig(
                RetentionConfig.newBuilder()
                    .setPeriod(Durations.fromDays(1))
                    .setPerPartitionBytes(30 * 1024 * 1024 * 1024L))
            .setReservationConfig(
                ReservationConfig.newBuilder().setThroughputReservation(reservationPath.toString()))
            .setName(topicPath.toString())
            .build();
    SubscriptionPath subscriptionPath =
        SubscriptionPath.newBuilder()
            .setLocation(cloudZone)
            .setProject(projectNumber)
            .setName(SubscriptionName.of(subscriptionId))
            .build();

    Subscription subscription =
        Subscription.newBuilder()
            .setDeliveryConfig(
                DeliveryConfig.newBuilder()
                    .setDeliveryRequirement(DeliveryRequirement.DELIVER_IMMEDIATELY))
            .setName(subscriptionPath.toString())
            .setTopic(topicPath.toString())
            .build();

    AdminClientSettings adminClientSettings =
        AdminClientSettings.newBuilder().setRegion(cloudRegion).build();
    try (AdminClient adminClient = AdminClient.create(adminClientSettings)) {
      adminClient.createTopic(topic).get();
      adminClient.createSubscription(subscription).get();
    }

    bout = new ByteArrayOutputStream();
    // Store current System.out
    console = System.out;
    out = new PrintStream(bout);
    System.setOut(out);
  }

  @After
  public void tearDown() throws Exception {
    // Use stored value for output stream
    System.setOut(console);
    TopicPath topicPath =
        TopicPath.newBuilder()
            .setProject(projectNumber)
            .setLocation(cloudZone)
            .setName(TopicName.of(topicId))
            .build();

    SubscriptionPath subscriptionPath =
        SubscriptionPath.newBuilder()
            .setLocation(cloudZone)
            .setProject(projectNumber)
            .setName(SubscriptionName.of(subscriptionId))
            .build();

    AdminClientSettings adminClientSettings =
        AdminClientSettings.newBuilder().setRegion(cloudRegion).build();

    try (AdminClient adminClient = AdminClient.create(adminClientSettings)) {
      adminClient.deleteTopic(topicPath).get();
      adminClient.deleteSubscription(subscriptionPath).get();
    }
  }

  @Test
  @Ignore
  public void testQuickstart() throws ExecutionException, InterruptedException {

    bout.reset();
    // Publish.
    ProducerExample.producerExample(cloudRegion.value(), zoneId, projectNumber.value(), topicId);
    assertThat(bout.toString())
        .contains(
            String.format(
                "Published 10 messages to projects/%s/locations/%s-%s/topics/%s",
                projectNumber.value(), cloudRegion.value(), zoneId, topicId));

    bout.reset();
    // Subscribe.
    ConsumerExample.consumerExample(
        cloudRegion.value(), zoneId, projectNumber.value(), topicId, subscriptionId);
    assertThat(bout.toString()).contains("Received 10 messages.");
  }

  @Test
  public void testKafkaPublish() throws ExecutionException, InterruptedException {
    bout.reset();
    KafkaProducerExample.kafkaProducerExample(
        cloudRegion.value(), zoneId, projectNumber.value(), topicId);
    assertThat(bout.toString())
        .contains(
            String.format(
                "Published 10 messages to projects/%s/locations/%s-%s/topics/%s",
                projectNumber.value(), cloudRegion.value(), zoneId, topicId));

    bout.reset();
    // Subscribe.
    ConsumerExample.consumerExample(
        cloudRegion.value(), zoneId, projectNumber.value(), topicId, subscriptionId);
    assertThat(bout.toString()).contains("Received 10 messages.");
  }
}
