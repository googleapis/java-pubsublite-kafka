# Google Pub/Sub Lite Kafka Shim Client for Java

Java idiomatic client for [Pub/Sub Lite Kafka Shim][product-docs].

[![Maven][maven-version-image]][maven-version-link]
![Stability][stability-image]

- [Product Documentation][product-docs]
- [Client Library Documentation][javadocs]

> Note: This client is a work-in-progress, and may occasionally
> make backwards-incompatible changes.

## Quickstart


If you are using Maven, add this to your pom.xml file:

```xml
<dependency>
  <groupId>com.google.cloud</groupId>
  <artifactId>pubsublite-kafka</artifactId>
  <version>0.2.3</version>
</dependency>
```

If you are using Gradle without BOM, add this to your dependencies
```Groovy
compile 'com.google.cloud:pubsublite-kafka:0.2.3'
```

If you are using SBT, add this to your dependencies
```Scala
libraryDependencies += "com.google.cloud" % "pubsublite-kafka" % "0.2.3"
```

## Authentication

See the [Authentication][authentication] section in the base directory's README.

## Getting Started

### Prerequisites

You will need a [Google Cloud Platform Console][developer-console] project with the Pub/Sub Lite Kafka Shim [API enabled][enable-api].
You will need to [enable billing][enable-billing] to use Google Pub/Sub Lite Kafka Shim.
[Follow these instructions][create-project] to get your project set up. You will also need to set up the local development environment by
[installing the Google Cloud SDK][cloud-sdk] and running the following commands in command line:
`gcloud auth login` and `gcloud config set project [YOUR PROJECT ID]`.

### Installation and setup

You'll need to obtain the `pubsublite-kafka` library.  See the [Quickstart](#quickstart) section
to add `pubsublite-kafka` as a dependency in your code.

## About Pub/Sub Lite Kafka Shim

Because [Google Cloud Pub/Sub Lite][product-docs] provides partitioned zonal data storage with
predefined capacity, a large portion of the Kafka Producer/Consumer API can
be implemented using Pub/Sub Lite as a backend. The key differences are:
- Pub/Sub Lite does not support transactions. All transaction methods on
  `Producer<byte[], byte[]>` will raise an exception.
- Producers operate on a single topic, and Consumers on a single subscription.
- ProducerRecord may not specify partition explicitly.
- Consumers may not dynamically create consumer groups (subscriptions).
- `Consumer.offsetsForTimes` and `Consumer.endOffsets` will raise an
  exception.



#### Publishing messages
With Pub/Sub Lite, you can use a `Producer<byte[], byte[]>` to publish messages:
```java
import com.google.cloud.pubsublite.kafka.ProducerSettings;
import org.apache.kafka.clients.producer.*;
import com.google.cloud.pubsublite.*;

...

private final static String ZONE = "us-central1-b";
private final static Long PROJECT_NUM = 123L;

...

TopicPath topic = TopicPath.newBuilder()
    .setLocation(CloudZone.parse(ZONE))
    .setProject(ProjectNumber.of(PROJECT_NUM))
    .setName(TopicName.of("my-topic")).build();

ProducerSettings settings = ProducerSettings.newBuilder()
    .setTopicPath(topic)
    .build();

try (Producer<byte[], byte[]> producer = settings.instantiate()) {
    Future<RecordMetadata> sent = producer.send(new ProducerRecord(
        topic.toString(),  // Required to be the same topic.
        "key".getBytes(),
        "value".getBytes()
    ));
    RecordMetadata meta = sent.get();
}
```
#### Receiving messages
With Pub/Sub Lite you can receive messages using a `Consumer<byte[], byte[]>`:
```java
import com.google.cloud.pubsublite.kafka.ConsumerSettings;
import org.apache.kafka.clients.consumer.*;
import com.google.cloud.pubsublite.*;
import com.google.cloud.pubsublite.cloudpubsub.FlowControlSettings;

...

private final static String ZONE = "us-central1-b";
private final static Long PROJECT_NUM = 123L;

...

SubscriptionPath subscription = SubscriptionPath.newBuilder()
    .setLocation(CloudZone.parse(ZONE))
    .setProject(ProjectNumber.of(PROJECT_NUM))
    .setName(SubscriptionName.of("my-sub"))
    .build();

ConsumerSettings settings = ConsumerSettings.newBuilder()
    .setSubscriptionPath(subscription)
    .setPerPartitionFlowControlSettings(FlowControlSettings.builder()
        .setBytesOutstanding(10_000_000)  // 10 MB
        .setMessagesOutstanding(Long.MAX_VALUE)
        .build())
    .setAutocommit(true);

try (Consumer<byte[], byte[]> consumer = settings.instantiate()) {
   while (true) {
     ConsumerRecords<byte[], byte[]> records = consumer.poll(Long.MAX_VALUE);
     for (ConsumerRecord<byte[], byte[]> record : records) {
       System.out.println(record.offset() + “: ” + record.value());
     }
   }
} catch (WakeupException e) {
   // ignored
}
```




## Samples

Samples are in the [`samples/`](https://github.com/googleapis/java-pubsublite-kafka/tree/master/samples) directory. The samples' `README.md`
has instructions for running the samples.

| Sample                      | Source Code                       | Try it |
| --------------------------- | --------------------------------- | ------ |
| Consumer Example | [source code](https://github.com/googleapis/java-pubsublite-kafka/blob/master/samples/snippets/src/main/java/pubsublite/ConsumerExample.java) | [![Open in Cloud Shell][shell_img]](https://console.cloud.google.com/cloudshell/open?git_repo=https://github.com/googleapis/java-pubsublite-kafka&page=editor&open_in_editor=samples/snippets/src/main/java/pubsublite/ConsumerExample.java) |
| Producer Example | [source code](https://github.com/googleapis/java-pubsublite-kafka/blob/master/samples/snippets/src/main/java/pubsublite/ProducerExample.java) | [![Open in Cloud Shell][shell_img]](https://console.cloud.google.com/cloudshell/open?git_repo=https://github.com/googleapis/java-pubsublite-kafka&page=editor&open_in_editor=samples/snippets/src/main/java/pubsublite/ProducerExample.java) |



## Troubleshooting

To get help, follow the instructions in the [shared Troubleshooting document][troubleshooting].

## Transport

Pub/Sub Lite Kafka Shim uses gRPC for the transport layer.

## Java Versions

Java 8 or above is required for using this client.

## Versioning


This library follows [Semantic Versioning](http://semver.org/).


It is currently in major version zero (``0.y.z``), which means that anything may change at any time
and the public API should not be considered stable.

## Contributing


Contributions to this library are always welcome and highly encouraged.

See [CONTRIBUTING][contributing] for more information how to get started.

Please note that this project is released with a Contributor Code of Conduct. By participating in
this project you agree to abide by its terms. See [Code of Conduct][code-of-conduct] for more
information.

## License

Apache 2.0 - See [LICENSE][license] for more information.

## CI Status

Java Version | Status
------------ | ------
Java 8 | [![Kokoro CI][kokoro-badge-image-2]][kokoro-badge-link-2]
Java 8 OSX | [![Kokoro CI][kokoro-badge-image-3]][kokoro-badge-link-3]
Java 8 Windows | [![Kokoro CI][kokoro-badge-image-4]][kokoro-badge-link-4]
Java 11 | [![Kokoro CI][kokoro-badge-image-5]][kokoro-badge-link-5]

Java is a registered trademark of Oracle and/or its affiliates.

[product-docs]: https://cloud.google.com/pubsub/lite/docs
[javadocs]: https://googleapis.dev/java/pubsublite-kafka/latest/index.html
[kokoro-badge-image-1]: http://storage.googleapis.com/cloud-devrel-public/java/badges/java-pubsublite-kafka/java7.svg
[kokoro-badge-link-1]: http://storage.googleapis.com/cloud-devrel-public/java/badges/java-pubsublite-kafka/java7.html
[kokoro-badge-image-2]: http://storage.googleapis.com/cloud-devrel-public/java/badges/java-pubsublite-kafka/java8.svg
[kokoro-badge-link-2]: http://storage.googleapis.com/cloud-devrel-public/java/badges/java-pubsublite-kafka/java8.html
[kokoro-badge-image-3]: http://storage.googleapis.com/cloud-devrel-public/java/badges/java-pubsublite-kafka/java8-osx.svg
[kokoro-badge-link-3]: http://storage.googleapis.com/cloud-devrel-public/java/badges/java-pubsublite-kafka/java8-osx.html
[kokoro-badge-image-4]: http://storage.googleapis.com/cloud-devrel-public/java/badges/java-pubsublite-kafka/java8-win.svg
[kokoro-badge-link-4]: http://storage.googleapis.com/cloud-devrel-public/java/badges/java-pubsublite-kafka/java8-win.html
[kokoro-badge-image-5]: http://storage.googleapis.com/cloud-devrel-public/java/badges/java-pubsublite-kafka/java11.svg
[kokoro-badge-link-5]: http://storage.googleapis.com/cloud-devrel-public/java/badges/java-pubsublite-kafka/java11.html
[stability-image]: https://img.shields.io/badge/stability-beta-yellow
[maven-version-image]: https://img.shields.io/maven-central/v/com.google.cloud/pubsublite-kafka.svg
[maven-version-link]: https://search.maven.org/search?q=g:com.google.cloud%20AND%20a:pubsublite-kafka&core=gav
[authentication]: https://github.com/googleapis/google-cloud-java#authentication
[developer-console]: https://console.developers.google.com/
[create-project]: https://cloud.google.com/resource-manager/docs/creating-managing-projects
[cloud-sdk]: https://cloud.google.com/sdk/
[troubleshooting]: https://github.com/googleapis/google-cloud-common/blob/master/troubleshooting/readme.md#troubleshooting
[contributing]: https://github.com/googleapis/java-pubsublite-kafka/blob/master/CONTRIBUTING.md
[code-of-conduct]: https://github.com/googleapis/java-pubsublite-kafka/blob/master/CODE_OF_CONDUCT.md#contributor-code-of-conduct
[license]: https://github.com/googleapis/java-pubsublite-kafka/blob/master/LICENSE
[enable-billing]: https://cloud.google.com/apis/docs/getting-started#enabling_billing
[enable-api]: https://console.cloud.google.com/flows/enableapi?apiid=pubsublite.googleapis.com
[libraries-bom]: https://github.com/GoogleCloudPlatform/cloud-opensource-java/wiki/The-Google-Cloud-Platform-Libraries-BOM
[shell_img]: https://gstatic.com/cloudssh/images/open-btn.png
