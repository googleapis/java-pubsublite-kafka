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

import com.google.cloud.pubsublite.proto.AttributeValues;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.protobuf.ByteString;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

class LiteHeaders implements Headers {
  private Map<String, AttributeValues> attributes;

  LiteHeaders(Map<String, AttributeValues> attributes) {
    this.attributes = attributes;
  }

  static Header toHeader(String key, ByteString value) {
    return new Header() {
      @Override
      public String key() {
        return key;
      }

      @Override
      public byte[] value() {
        return value.toByteArray();
      }
    };
  }

  @Override
  public Headers add(Header header) throws IllegalStateException {
    throw new IllegalStateException();
  }

  @Override
  public Headers add(String s, byte[] bytes) throws IllegalStateException {
    throw new IllegalStateException();
  }

  @Override
  public Headers remove(String s) throws IllegalStateException {
    throw new IllegalStateException();
  }

  @Override
  public Header lastHeader(String s) {
    return Iterables.getLast(this);
  }

  @Override
  public Iterable<Header> headers(String s) {
    @Nullable AttributeValues values = attributes.get(s);
    if (values == null) {
      return ImmutableList.of();
    }
    return values.getValuesList().stream().map(v -> toHeader(s, v)).collect(Collectors.toList());
  }

  @Override
  public Header[] toArray() {
    return Iterators.toArray(iterator(), Header.class);
  }

  @Override
  public Iterator<Header> iterator() {
    return attributes.entrySet().stream()
        .flatMap(
            entry ->
                entry.getValue().getValuesList().stream().map(v -> toHeader(entry.getKey(), v)))
        .iterator();
  }
}
