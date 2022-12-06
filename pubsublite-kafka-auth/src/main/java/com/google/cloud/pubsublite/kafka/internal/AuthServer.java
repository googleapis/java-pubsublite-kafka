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

package com.google.cloud.pubsublite.kafka.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

public class AuthServer {
  public static int PORT = 14293;
  public static InetSocketAddress ADDRESS =
      new InetSocketAddress(InetAddress.getLoopbackAddress(), PORT);

  private static final String HEADER =
      new Gson().toJson(ImmutableMap.of("typ", "JWT", "alg", "GOOG_TOKEN"));

  static {
    spawnDaemon();
  }

  private static String b64Encode(String data) {
    return Base64.getUrlEncoder().encodeToString(data.getBytes(UTF_8));
  }

  private static String getJwt(AccessToken token) {
    return new Gson()
        .toJson(
            ImmutableMap.of(
                "exp",
                token.getExpirationTime().toInstant().toEpochMilli(),
                "iat",
                System.currentTimeMillis(),
                "scope",
                "pubsub",
                "sub",
                "unused"));
  }

  private static String getKafkaAccessToken(AccessToken token) {
    return String.join(
        ".", b64Encode(HEADER), b64Encode(getJwt(token)), b64Encode(token.getTokenValue()));
  }

  private static String getResponse(GoogleCredentials creds) throws IOException {
    creds.refreshIfExpired();
    AccessToken token = creds.getAccessToken();
    long exipiresInSeconds =
        Duration.between(Instant.now(), token.getExpirationTime().toInstant()).getSeconds();
    return new Gson()
        .toJson(
            ImmutableMap.of(
                "access_token",
                getKafkaAccessToken(token),
                "token_type",
                "bearer",
                "expires_in",
                Long.toString(exipiresInSeconds)));
  }

  private static void spawnDaemon() {
    // Run spawn() in a daemon thread so the created threads are themselves daemons.
    Thread thread = new Thread(AuthServer::spawn);
    thread.setDaemon(true);
    thread.start();
  }

  private static void spawn() {
    try {
      GoogleCredentials creds = GoogleCredentials.getApplicationDefault();
      HttpServer server = HttpServer.create(ADDRESS, 0);
      server.createContext(
          "/",
          handler -> {
            String response = getResponse(creds);
            handler.sendResponseHeaders(200, response.length());
            handler.getResponseBody().write(response.getBytes(UTF_8));
            handler.close();
          });
      server.start();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
