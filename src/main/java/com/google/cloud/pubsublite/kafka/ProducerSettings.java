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

import static com.google.cloud.pubsublite.cloudpubsub.PublisherSettings.DEFAULT_BATCHING_SETTINGS;
import static com.google.cloud.pubsublite.internal.ExtractStatus.toCanonical;
import static com.google.cloud.pubsublite.internal.wire.ServiceClients.addDefaultSettings;
import static com.google.cloud.pubsublite.internal.wire.ServiceClients.getCallContext;

import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.ApiException;
import com.google.auto.value.AutoValue;
import com.google.cloud.pubsublite.AdminClient;
import com.google.cloud.pubsublite.AdminClientSettings;
import com.google.cloud.pubsublite.MessageMetadata;
import com.google.cloud.pubsublite.Partition;
import com.google.cloud.pubsublite.TopicPath;
import com.google.cloud.pubsublite.internal.wire.*;
import com.google.cloud.pubsublite.internal.wire.PubsubContext.Framework;
import com.google.cloud.pubsublite.v1.PublisherServiceClient;
import com.google.cloud.pubsublite.v1.PublisherServiceSettings;
import org.apache.kafka.clients.producer.Producer;

@AutoValue
public abstract class ProducerSettings {
  private static final Framework FRAMEWORK = Framework.of("KAFKA_SHIM");

  // Required parameters.
  abstract TopicPath topicPath();

  public static Builder newBuilder() {
    return new AutoValue_ProducerSettings.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    // Required parameters.
    public abstract Builder setTopicPath(TopicPath path);

    public abstract ProducerSettings build();
  }

  private AdminClient newAdminClient() {
    return AdminClient.create(
        AdminClientSettings.newBuilder().setRegion(topicPath().location().extractRegion()).build());
  }

  private PublisherServiceClient newServiceClient() throws ApiException {
    try {
      return PublisherServiceClient.create(
          addDefaultSettings(
              topicPath().location().extractRegion(), PublisherServiceSettings.newBuilder()));
    } catch (Throwable t) {
      throw toCanonical(t).underlying;
    }
  }

  private PartitionPublisherFactory getPartitionPublisherFactory() {
    PublisherServiceClient client = newServiceClient();
    return new PartitionPublisherFactory() {
      @Override
      public com.google.cloud.pubsublite.internal.Publisher<MessageMetadata> newPublisher(
          Partition partition) throws ApiException {
        SinglePartitionPublisherBuilder.Builder singlePartitionBuilder =
            SinglePartitionPublisherBuilder.newBuilder()
                .setTopic(topicPath())
                .setPartition(partition)
                .setBatchingSettings(DEFAULT_BATCHING_SETTINGS)
                .setStreamFactory(
                    responseStream -> {
                      ApiCallContext context =
                          getCallContext(
                              PubsubContext.of(FRAMEWORK),
                              RoutingMetadata.of(topicPath(), partition));
                      return client.publishCallable().splitCall(responseStream, context);
                    });
        return singlePartitionBuilder.build();
      }

      @Override
      public void close() {
        client.close();
      }
    };
  }

  public Producer<byte[], byte[]> instantiate() throws ApiException {
    PartitionCountWatchingPublisherSettings publisherSettings =
        PartitionCountWatchingPublisherSettings.newBuilder()
            .setTopic(topicPath())
            .setAdminClient(newAdminClient())
            .setPublisherFactory(getPartitionPublisherFactory())
            .build();
    SharedBehavior shared = new SharedBehavior(newAdminClient());
    return new PubsubLiteProducer(publisherSettings.instantiate(), shared, topicPath());
  }
}
