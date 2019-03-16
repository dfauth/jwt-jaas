package com.github.dfauth.jwt_jaas.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;


public class KafkaProducer<V> {

    private static final Logger logger = LoggerFactory.getLogger(KafkaProducer.class);

    private final String topic;
    private final Map<String, Object> props;
    private org.apache.kafka.clients.producer.KafkaProducer<String, V> producer;

    public KafkaProducer(String topic, String groupId, String zookeeperConnectString, String brokerList, Map<String, Object> props) {
        this.topic = topic;
        this.props = new HashMap(props);
        props.put("bootstrap.servers", brokerList);
        props.put("key.serializer", StringSerializer.class);
        props.put("value.serializer", StringSerializer.class);
        props.put("client.id", groupId);
    }

    public KafkaProducer start() {
        this.producer = new org.apache.kafka.clients.producer.KafkaProducer<String, V>(props);
        return this;
    }

    public KafkaProducer stop() {
        this.producer.close();
        return this;
    }

    public Optional<CompletableFuture<RecordMetadata>> send(V message) {
        return StreamSupport.stream(send(Collections.singleton(message)).spliterator(), false).findFirst();
    }

    public Iterable<CompletableFuture<RecordMetadata>> send(Collection<V> messages) {
        return messages.stream().
                map(m -> new ProducerRecord<String, V>(topic, m)).
                map(r -> producer.send(r, (RecordMetadata m, Exception e) -> {
                    if(e == null) {
                        logger.info("metadata: "+m);
                    } else {
                        logger.error(e.getMessage(),e);
                    }
                })).
                map(f -> CompletableFuture.supplyAsync(() -> {
                        try {
                            return f.get();
                        } catch (InterruptedException e) {
                            logger.error(e.getMessage(), e);
                            throw new RuntimeException(e);
                        } catch (ExecutionException e) {
                            logger.error(e.getMessage(), e);
                            throw new RuntimeException(e);
                        }
                    })
                ).collect(Collectors.toSet());
    }

    public static class NestedBuilder implements Builder<KafkaProducer> {

        private final String topic;
        private String groupId = UUID.randomUUID().toString();
        private String zookeeperConnect = "localhost:2181";
        private String brokerList = "localhost:9092";
        private Map<String, Object> props = Collections.emptyMap();

        public static NestedBuilder of(String topic) {
            return new NestedBuilder(topic);
        }

        public NestedBuilder(String topic) {
            this.topic = topic;
        }

        public NestedBuilder withGroupId(String groupId) {
            this.groupId = groupId;
            return this;
        }

        public NestedBuilder withZookeeperConnect(String zk) {
            this.zookeeperConnect = zk;
            return this;
        }

        public NestedBuilder withBrokerList(String bl) {
            this.brokerList = bl;
            return this;
        }

        public NestedBuilder withProperties(Map<String, Object> props) {
            this.props = props;
            return this;
        }

        @Override
        public KafkaProducer build() {
            return new KafkaProducer(topic, groupId, zookeeperConnect, brokerList, props);
        }
    }
}
