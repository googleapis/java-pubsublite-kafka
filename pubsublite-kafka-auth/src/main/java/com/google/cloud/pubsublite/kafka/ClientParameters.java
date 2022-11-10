/*
 * Copyright 2022 Google LLC
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

import com.google.cloud.pubsublite.CloudRegion;
import com.google.cloud.pubsublite.ProjectId;
import com.google.cloud.pubsublite.ProjectIdOrNumber;
import com.google.cloud.pubsublite.ProjectNumber;
import com.google.cloud.pubsublite.kafka.internal.AuthServer;
import java.util.HashMap;
import java.util.Map;

/** A class providing the correct parameters for connecting a Kafka client to Pub/Sub Lite. */
public final class ClientParameters {
  public static Map<String, Object> getProducerParams(ProjectId project, CloudRegion region) {
    return getProducerParams(ProjectIdOrNumber.of(project), region);
  }

  public static Map<String, Object> getProducerParams(ProjectNumber project, CloudRegion region) {
    return getProducerParams(ProjectIdOrNumber.of(project), region);
  }

  public static Map<String, Object> getProducerParams(
      ProjectIdOrNumber project, CloudRegion region) {
    HashMap<String, Object> params = new HashMap<>();
    params.put("enable.idempotence", false);
    params.put("bootstrap.servers", getEndpoint(region));
    params.put("security.protocol", "SASL_SSL");
    params.put("sasl.mechanism", "OAUTHBEARER");
    params.put("sasl.oauthbearer.token.endpoint.url", AuthServer.ADDRESS.toString());
    params.put("sasl.jaas.config", getJaasConfig(project));
    return params;
  }

  private static String getEndpoint(CloudRegion region) {
    return region.value() + "-kafka-pubsub.googleapis.com";
  }

  private static String getJaasConfig(ProjectIdOrNumber project) {
    return String.format(
        "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required clientId=\"unused\" clientSecret=\"unused\" extension_pubsubProject=\"%s\";",
        project);
  }

  private ClientParameters() {}
}
