package com.github.dfauth.jwt_jaas.kafka;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;


public abstract class AbstractKafkaBuilder<T> implements Builder<T> {

    protected final String topic;
    protected String groupId = UUID.randomUUID().toString();
    protected String zookeeperConnect = "localhost:2181";
    protected String brokerList = "localhost:9092";
    protected Map<String, Object> props = Collections.emptyMap();

    public AbstractKafkaBuilder(String topic) {
        this.topic = topic;
    }

    public AbstractKafkaBuilder withGroupId(String groupId) {
        this.groupId = groupId;
        return this;
    }

    public AbstractKafkaBuilder withZookeeperConnect(String zk) {
        this.zookeeperConnect = zk;
        return this;
    }

    public AbstractKafkaBuilder withBrokerList(String bl) {
        this.brokerList = bl;
        return this;
    }

    public AbstractKafkaBuilder withProperties(Map<String, Object> props) {
        this.props = props;
        return this;
    }
}
