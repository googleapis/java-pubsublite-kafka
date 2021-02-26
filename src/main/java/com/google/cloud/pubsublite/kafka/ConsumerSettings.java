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

import static com.google.cloud.pubsublite.internal.ExtractStatus.toCanonical;

import com.google.api.gax.rpc.ApiException;
import com.google.auto.value.AutoValue;
import com.google.cloud.pubsublite.AdminClient;
import com.google.cloud.pubsublite.AdminClientSettings;
import com.google.cloud.pubsublite.CloudZone;
import com.google.cloud.pubsublite.SubscriptionPath;
import com.google.cloud.pubsublite.TopicPath;
import com.google.cloud.pubsublite.cloudpubsub.FlowControlSettings;
import com.google.cloud.pubsublite.internal.BufferingPullSubscriber;
import com.google.cloud.pubsublite.internal.CursorClient;
import com.google.cloud.pubsublite.internal.CursorClientSettings;
import com.google.cloud.pubsublite.internal.wire.AssignerFactory;
import com.google.cloud.pubsublite.internal.wire.AssignerSettings;
import com.google.cloud.pubsublite.internal.wire.CommitterSettings;
import com.google.cloud.pubsublite.internal.wire.PubsubContext;
import com.google.cloud.pubsublite.internal.wire.PubsubContext.Framework;
import com.google.cloud.pubsublite.internal.wire.RoutingMetadata;
import com.google.cloud.pubsublite.internal.wire.ServiceClients;
import com.google.cloud.pubsublite.internal.wire.SubscriberBuilder;
import com.google.cloud.pubsublite.internal.wire.SubscriberFactory;
import com.google.cloud.pubsublite.proto.Subscription;
import com.google.cloud.pubsublite.v1.CursorServiceClient;
import com.google.cloud.pubsublite.v1.CursorServiceSettings;
import com.google.cloud.pubsublite.v1.PartitionAssignmentServiceClient;
import com.google.cloud.pubsublite.v1.PartitionAssignmentServiceSettings;
import com.google.cloud.pubsublite.v1.SubscriberServiceClient;
import com.google.cloud.pubsublite.v1.SubscriberServiceSettings;
import org.apache.kafka.clients.consumer.Consumer;

@AutoValue
public abstract class ConsumerSettings {
  private static final Framework FRAMEWORK = Framework.of("KAFKA_SHIM");

  // Required parameters.
  abstract SubscriptionPath subscriptionPath();

  abstract FlowControlSettings perPartitionFlowControlSettings();

  // Optional parameters.
  abstract boolean autocommit();

  public static Builder newBuilder() {
    return (new AutoValue_ConsumerSettings.Builder()).setAutocommit(false);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    // Required parameters.
    public abstract Builder setSubscriptionPath(SubscriptionPath path);

    public abstract Builder setPerPartitionFlowControlSettings(FlowControlSettings settings);

    // Optional parameters.
    public abstract Builder setAutocommit(boolean autocommit);

    public abstract ConsumerSettings build();
  }

  public Consumer<byte[], byte[]> instantiate() throws ApiException {
    CloudZone zone = subscriptionPath().location();
    try (AdminClient adminClient =
        AdminClient.create(AdminClientSettings.newBuilder().setRegion(zone.region()).build())) {
      Subscription subscription = adminClient.getSubscription(subscriptionPath()).get();
      TopicPath topic = TopicPath.parse(subscription.getTopic());
      AssignerFactory assignerFactory =
          receiver -> {
            try {
              return AssignerSettings.newBuilder()
                  .setReceiver(receiver)
                  .setSubscriptionPath(subscriptionPath())
                  .setServiceClient(
                      PartitionAssignmentServiceClient.create(
                          ServiceClients.addDefaultSettings(
                              subscriptionPath().location().region(),
                              PartitionAssignmentServiceSettings.newBuilder())))
                  .build()
                  .instantiate();
            } catch (Throwable t) {
              throw toCanonical(t).underlying;
            }
          };
      PullSubscriberFactory pullSubscriberFactory =
          (partition, initialSeek) -> {
            SubscriberFactory subscriberFactory =
                consumer -> {
                  try {
                    return SubscriberBuilder.newBuilder()
                        .setPartition(partition)
                        .setSubscriptionPath(subscriptionPath())
                        .setMessageConsumer(consumer)
                        .setServiceClient(
                            SubscriberServiceClient.create(
                                ServiceClients.addDefaultSettings(
                                    subscriptionPath().location().region(),
                                    ServiceClients.addDefaultMetadata(
                                        PubsubContext.of(FRAMEWORK),
                                        RoutingMetadata.of(subscriptionPath(), partition),
                                        SubscriberServiceSettings.newBuilder()))))
                        .build();
                  } catch (Throwable t) {
                    throw toCanonical(t).underlying;
                  }
                };
            return new BufferingPullSubscriber(
                subscriberFactory, perPartitionFlowControlSettings(), initialSeek);
          };
      CommitterFactory committerFactory =
          partition -> {
            try {
              return CommitterSettings.newBuilder()
                  .setSubscriptionPath(subscriptionPath())
                  .setPartition(partition)
                  .setServiceClient(
                      CursorServiceClient.create(
                          ServiceClients.addDefaultSettings(
                              subscriptionPath().location().region(),
                              CursorServiceSettings.newBuilder())))
                  .build()
                  .instantiate();
            } catch (Throwable t) {
              throw toCanonical(t);
            }
          };
      ConsumerFactory consumerFactory =
          () ->
              new SingleSubscriptionConsumerImpl(
                  topic, autocommit(), pullSubscriberFactory, committerFactory);

      CursorClient cursorClient =
          CursorClient.create(CursorClientSettings.newBuilder().setRegion(zone.region()).build());
      SharedBehavior shared =
          new SharedBehavior(
              AdminClient.create(
                  AdminClientSettings.newBuilder().setRegion(topic.location().region()).build()));
      return new PubsubLiteConsumer(
          subscriptionPath(), topic, shared, consumerFactory, assignerFactory, cursorClient);
    } catch (Exception e) {
      throw toCanonical(e).underlying;
    }
  }
}
