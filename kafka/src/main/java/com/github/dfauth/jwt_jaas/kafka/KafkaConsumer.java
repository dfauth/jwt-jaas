package com.github.dfauth.jwt_jaas.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.utils.SystemTime;
import org.apache.kafka.common.utils.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.StreamSupport;

import static java.util.concurrent.TimeUnit.SECONDS;


public class KafkaConsumer<V> {

    private static final Logger logger = LoggerFactory.getLogger(KafkaProducer.class);

    private final String topic;
    private final Map<String, Object> props;
    private final Deserializer<V> deserializer;
    private org.apache.kafka.clients.consumer.KafkaConsumer<String, V> consumer;
    private static final SystemTime systemTime = new SystemTime();

    public KafkaConsumer(String topic, String groupId, String zookeeperConnect, String brokerList, Map<String, Object> props, Deserializer<V> d) {
        this.topic = topic;
        this.props = new HashMap(props);
        this.props.put("bootstrap.servers", brokerList);
        this.props.put("auto.offset.reset", "earliest");
        this.props.put("enable.auto.commit", "false");
        this.props.put("group.id", groupId);
        this.deserializer = d;
    }

    public KafkaConsumer<V> stop() {
        this.consumer.close();
        return this;
    }

    public void subscribe(Function<V, CompletableFuture<Boolean>> f) {
        subscribe(f, 5000);
    }

    public void subscribe(Function<V, CompletableFuture<Boolean>> f, long timeout) {
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                this.consumer = new org.apache.kafka.clients.consumer.KafkaConsumer(this.props, new StringDeserializer(), deserializer);
                consumer.subscribe(Collections.singleton(topic));
                consumer.partitionsFor(topic).stream().forEach(i -> logger.info("partionInfo: "+i));

                while(true) {
                    try {
                        ConsumerRecords<String, V> records = consumer.poll(Duration.ofMillis(timeout));
                        Iterator<ConsumerRecord<String, V>> it = records.iterator();
                        while(it.hasNext()) {
                            ConsumerRecord<String, V> record = it.next();
                            CompletableFuture<Boolean> future = f.apply(record.value());
                            future.thenAccept(b -> {if(b) {
                                TopicPartition tp = new TopicPartition(record.topic(), record.partition());
                                OffsetAndMetadata om = new OffsetAndMetadata(record.offset() + 1);
                                consumer.commitSync(Collections.singletonMap(tp, om));
                            }});
                        }
                    } catch (RuntimeException e) {
                        logger.info(e.getMessage(), e);
                    }
                }
            } finally {
                consumer.unsubscribe();
            }
        });
    }

    public Optional<V> getOneMessage() {
        return getOneMessage(5000);
    }

    public Optional<V> getOneMessage(long timeout) {
        return StreamSupport.stream(getMessages(1, timeout).spliterator(), false).findFirst();
    }

    public Iterable<V> getMessages(int n) {
        return getMessages(n, SECONDS.toMillis(5));
    }
    public Iterable<V> getMessages(int n, long timeout) {
        this.consumer = new org.apache.kafka.clients.consumer.KafkaConsumer(this.props, new StringDeserializer(), deserializer);
        try {
            consumer.subscribe(Collections.singleton(topic));
            consumer.partitionsFor(topic).stream().forEach(i -> logger.info("partionInfo: "+i));

            List<V> tmp = new ArrayList(n);

            Timer timer = timer(timeout);
            while(tmp.size() < n && timer.notExpired()) {
                ConsumerRecords<String, V> records = consumer.poll(Duration.ofSeconds(5));
                Iterator<ConsumerRecord<String, V>> it = records.iterator();
                while(it.hasNext()) {
                    ConsumerRecord<String, V> record = it.next();
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


    public static class NestedBuilder<V> extends AbstractKafkaBuilder<KafkaConsumer> {

        private Deserializer<V> deserializer;

        public static NestedBuilder of(String topic) {
            return new NestedBuilder(topic);
        }

        public NestedBuilder(String topic) {
            super(topic);
        }

        public NestedBuilder withDeserializer(Deserializer<V> deserializer) {
            this.deserializer = deserializer;
            return this;
        }

        @Override
        public KafkaConsumer build() {
            return new KafkaConsumer(topic, groupId, zookeeperConnect, brokerList, props, deserializer);
        }
    }
}
