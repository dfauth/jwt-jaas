package com.github.dfauth.jwt_jaas.kafka

import net.manub.embeddedkafka.EmbeddedKafka
import org.scalatest.{FlatSpec, Matchers}

class AnotherEmbeddedKafkaSpec
  extends FlatSpec
    with Matchers
    with EmbeddedKafka {

  val TOPIC = "testTopic"

  "runs with embedded kafka and manually starts/stops the server" should "work for strings" in {

//    val config = EmbeddedKafkaConfig(kafkaPort = 0, zooKeeperPort = 0)

    try {
      val kafka = EmbeddedKafka.start()

//      withRunningKafkaOnFoundPort(config) { implicit actualConfig =>
//val zookeeperConnectString = "localhost:"+actualConfig.zooKeeperPort
//      val brokerList = "localhost:"+actualConfig.kafkaPort
//      val producer = new KafkaSink[String](TOPIC, zookeeperConnect = zookeeperConnectString, brokerList = brokerList)
//      val msg = producer.send("testMessage")
//      val consumer = new KafkaSource[String](TOPIC, zookeeperConnect = zookeeperConnectString, brokerList = brokerList)

        val producer = new KafkaSink[String](TOPIC)
        val msg = producer.send("testMessage")
        val consumer = new KafkaSource[String](TOPIC)
        msg should be ("testMessage")


    } finally {
      EmbeddedKafka.stop()
    }
  }

}