package com.github.dfauth.jwt_jaas.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.utils.SystemTime;
import org.apache.kafka.common.utils.Timer;

import java.time.Duration;
import java.util.*;
import java.util.stream.StreamSupport;

import static java.util.concurrent.TimeUnit.SECONDS;


public class KafkaConsumer<V> {

    private final String topic;
    private final Map<String, Object> props;
    private org.apache.kafka.clients.consumer.KafkaConsumer<String, String> consumer;
    private static final SystemTime systemTime = new SystemTime();

    public KafkaConsumer(String topic, String groupId, String zookeeperConnect, String brokerList, Map<String, Object> props) {
        this.topic = topic;
        this.props = new HashMap(props);
        this.props.put("bootstrap.servers", brokerList);
//        this.props.put("key.serializer", StringDeserializer.class);
//        this.props.put("value.serializer", StringDeserializer.class);
        this.props.put("group.id", groupId);
        this.consumer = new org.apache.kafka.clients.consumer.KafkaConsumer(this.props, new StringDeserializer(), new StringDeserializer());
    }

    public KafkaConsumer<V> stop() {
        this.consumer.close();
        return this;
    }

    public Optional<V> getOneMessage() {
        return StreamSupport.stream(getMessages(1).spliterator(), false).findFirst();
    }

    public Iterable<V> getMessages(int n) {
        return getMessages(n, SECONDS.toMillis(5));
    }
    public Iterable<V> getMessages(int n, long timeout) {
        try {
            consumer.subscribe(Collections.singleton(topic));
            consumer.partitionsFor(topic);

            List tmp = new ArrayList(n);

            Timer timer = timer(timeout);
            while(tmp.size() < n && timer.notExpired()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
                Iterator<ConsumerRecord<String, String>> it = records.iterator();
                while(it.hasNext()) {
                    ConsumerRecord<String, String> record = it.next();
                    tmp.add(record.value());
                    TopicPartition tp = new TopicPartition(record.topic(), record.partition());
                    OffsetAndMetadata om = new OffsetAndMetadata(record.offset() + 1);
                    consumer.commitSync(Collections.singletonMap(tp, om));
                }
                timer.update();
            }
            return tmp;
        } finally {
            consumer.unsubscribe();
        }
    }

    private Timer timer(long timeout) {
        return systemTime.timer(timeout);
    }


    public static class NestedBuilder extends AbstractKafkaBuilder<KafkaConsumer> {

        public static NestedBuilder of(String topic) {
            return new NestedBuilder(topic);
        }

        public NestedBuilder(String topic) {
            super(topic);
        }

        @Override
        public KafkaConsumer build() {
            return new KafkaConsumer(topic, groupId, zookeeperConnect, brokerList, props);
        }
    }
}
