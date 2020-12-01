# Instructions for Pub/Sub Lite Kafka usage.

1. Add the following to your POM file to download the Pub/Sub Lite Kafka shim.
```xml
<dependency>
  <groupId>org.apache.kafka</groupId>
  <artifactId>kafka-clients</artifactId>
  <version>2.6.0</version>
</dependency>
<dependency>
  <groupId>com.google.cloud</groupId>
  <artifactId>pubsublite-kafka</artifactId>
  <version>0.1.1</version>
</dependency>
```

1. Create a topic using `gcloud pubsub lite-topics create`
1. Write some messages using:

    ```java
    import com.google.cloud.pubsublite.kafka.ProducerSettings;
    import com.google.cloud.pubsublite.*;

    import org.apache.kafka.clients.producer.*;
    
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
1. Create a subscription using `gcloud pubsub lite-subscriptions create`
1. Read some messages using:

    ```java
    import com.google.cloud.pubsublite.cloudpubsub.FlowControlSettings;
    import com.google.cloud.pubsublite.kafka.ConsumerSettings;
    import com.google.cloud.pubsublite.*;
 
    import org.apache.kafka.clients.consumer.*;
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
        .setAutocommit(true)
        .build();
   
    try (Consumer<byte[], byte[]> consumer = settings.instantiate()) {
       while (true) {
         ConsumerRecords<byte[], byte[]> records = consumer.poll(Duration.ofSeconds(30));
         for (ConsumerRecord<byte[], byte[]> record : records) {
           System.out.println(record.offset() + “: ” + record.value());
         }
       }
    } catch (WakeupException e) {
       // ignored
    }
    ```
