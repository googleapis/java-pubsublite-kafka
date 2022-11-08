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
import static com.google.cloud.pubsublite.internal.wire.ServiceClients.addDefaultSettings;
import static com.google.cloud.pubsublite.internal.wire.ServiceClients.getCallContext;

import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.ApiException;
import com.google.auto.value.AutoValue;
import com.google.cloud.pubsublite.AdminClient;
import com.google.cloud.pubsublite.AdminClientSettings;
import com.google.cloud.pubsublite.CloudRegion;
import com.google.cloud.pubsublite.SubscriptionPath;
import com.google.cloud.pubsublite.TopicPath;
import com.google.cloud.pubsublite.cloudpubsub.FlowControlSettings;
import com.google.cloud.pubsublite.internal.BlockingPullSubscriberImpl;
import com.google.cloud.pubsublite.internal.CursorClient;
import com.google.cloud.pubsublite.internal.CursorClientSettings;
import com.google.cloud.pubsublite.internal.TopicStatsClient;
import com.google.cloud.pubsublite.internal.TopicStatsClientSettings;
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
import java.util.Optional;
import org.apache.kafka.clients.consumer.Consumer;

@AutoValue
public abstract class ConsumerSettings {
  private static final Framework FRAMEWORK = Framework.of("KAFKA_SHIM");

  // Required parameters.
  abstract SubscriptionPath subscriptionPath();

  abstract FlowControlSettings perPartitionFlowControlSettings();

  // Optional parameters.
  abstract boolean autocommit();

  abstract Optional<TopicPath> topicPathOverride();

  public static Builder newBuilder() {
    return new AutoValue_ConsumerSettings.Builder().setAutocommit(false);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    // Required parameters.
    /**
     * The subscription path to use. Only the topic corresponding to this subscription can be
     * subscribed to.
     */
    public abstract Builder setSubscriptionPath(SubscriptionPath path);

    /** The per-partition flow control settings. */
    public abstract Builder setPerPartitionFlowControlSettings(FlowControlSettings settings);

    // Optional parameters.
    /** The autocommit mode. */
    public abstract Builder setAutocommit(boolean autocommit);

    /**
     * An override for the TopicPath used by this consumer.
     *
     * <p>When this is set, the topic path of the subscription will not be fetched: instead, the
     * topic used in methods will be compared with the provided TopicPath object.
     *
     * <p>This is useful if you do not have the pubsublite.subscriptions.get permission for the
     * subscription.
     */
    public abstract Builder setTopicPathOverride(TopicPath topicPath);

    public abstract ConsumerSettings build();
  }

  public Consumer<byte[], byte[]> instantiate() throws ApiException {
    try {
      CloudRegion region = subscriptionPath().location().extractRegion();
      TopicPath topic;
      if (topicPathOverride().isPresent()) {
        topic = topicPathOverride().get();
      } else {
        try (AdminClient adminClient =
            AdminClient.create(AdminClientSettings.newBuilder().setRegion(region).build())) {
          Subscription subscription = adminClient.getSubscription(subscriptionPath()).get();
          topic = TopicPath.parse(subscription.getTopic());
        }
      }
      AssignerFactory assignerFactory =
          receiver -> {
            try {
              return AssignerSettings.newBuilder()
                  .setReceiver(receiver)
                  .setSubscriptionPath(subscriptionPath())
                  .setServiceClient(
                      PartitionAssignmentServiceClient.create(
                          ServiceClients.addDefaultSettings(
                              region, PartitionAssignmentServiceSettings.newBuilder())))
                  .build()
                  .instantiate();
            } catch (Throwable t) {
              throw toCanonical(t).underlying;
            }
          };
      SubscriberServiceClient subscriberServiceClient =
          SubscriberServiceClient.create(
              ServiceClients.addDefaultSettings(region, SubscriberServiceSettings.newBuilder()));
      PullSubscriberFactory pullSubscriberFactory =
          (partition, initialSeek, resetHandler) -> {
            SubscriberFactory subscriberFactory =
                consumer -> {
                  try {
                    return SubscriberBuilder.newBuilder()
                        .setPartition(partition)
                        .setSubscriptionPath(subscriptionPath())
                        .setMessageConsumer(consumer)
                        .setStreamFactory(
                            responseStream -> {
                              ApiCallContext context =
                                  getCallContext(
                                      PubsubContext.of(FRAMEWORK),
                                      RoutingMetadata.of(subscriptionPath(), partition));
                              return subscriberServiceClient
                                  .subscribeCallable()
                                  .splitCall(responseStream, context);
                            })
                        .setInitialLocation(initialSeek)
                        .setResetHandler(resetHandler)
                        .build();
                  } catch (Throwable t) {
                    throw toCanonical(t).underlying;
                  }
                };
            return new BlockingPullSubscriberImpl(
                subscriberFactory, perPartitionFlowControlSettings());
          };
      CursorServiceClient cursorServiceClient =
          CursorServiceClient.create(
              addDefaultSettings(
                  subscriptionPath().location().extractRegion(),
                  CursorServiceSettings.newBuilder()));
      CommitterFactory committerFactory =
          partition -> {
            try {
              return CommitterSettings.newBuilder()
                  .setSubscriptionPath(subscriptionPath())
                  .setPartition(partition)
                  .setStreamFactory(
                      responseStream ->
                          cursorServiceClient
                              .streamingCommitCursorCallable()
                              .splitCall(responseStream))
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
          CursorClient.create(CursorClientSettings.newBuilder().setRegion(region).build());
      TopicStatsClient topicStatsClient =
          TopicStatsClient.create(TopicStatsClientSettings.newBuilder().setRegion(region).build());
      SharedBehavior shared =
          new SharedBehavior(
              AdminClient.create(AdminClientSettings.newBuilder().setRegion(region).build()));
      return new PubsubLiteConsumer(
          subscriptionPath(),
          topic,
          shared,
          consumerFactory,
          assignerFactory,
          cursorClient,
          topicStatsClient,
          cursorServiceClient,
          subscriberServiceClient);
    } catch (Exception e) {
      throw toCanonical(e).underlying;
    }
  }
}
