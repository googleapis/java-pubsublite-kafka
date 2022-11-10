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
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;

public class AuthServer {
  public static int PORT = 14293;
  public static InetSocketAddress ADDRESS =
      new InetSocketAddress(InetAddress.getLoopbackAddress(), PORT);

  static {
    spawn();
  }

  private static String getResponse(GoogleCredentials creds) {
    AccessToken token = creds.getAccessToken();
    long exipiresInSeconds =
        Duration.between(Instant.now(), token.getExpirationTime().toInstant()).getSeconds();
    return String.format(
        "{\"access_token\": \"%s\"," + "\"token_type\": \"bearer\"," + "\"expires_in\": \"%s\"}",
        token.getTokenValue(), exipiresInSeconds);
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
