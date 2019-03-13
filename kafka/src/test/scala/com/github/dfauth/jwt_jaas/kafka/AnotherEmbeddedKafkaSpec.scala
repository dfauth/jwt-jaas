package com.github.dfauth.jwt_jaas.kafka

import com.typesafe.scalalogging.LazyLogging
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.scalatest.{FlatSpec, Matchers}

class AnotherEmbeddedKafkaSpec
  extends FlatSpec
    with Matchers
    with EmbeddedKafka
    with LazyLogging {

  val TOPIC = "testTopic"

  "runs with embedded kafka and manually starts/stops the server" should "work for strings" in {

    try {
      // val kafka = EmbeddedKafka.start()

      withRunningKafkaOnFoundPort(EmbeddedKafkaConfig(kafkaPort = 9092, zooKeeperPort = 2181)) { implicit config =>
        val zookeeperConnectString = "localhost:" + config.zooKeeperPort
        val brokerList = "localhost:" + config.kafkaPort
        val producer = new KafkaSink[String](TOPIC,
          zookeeperConnect = zookeeperConnectString,
          brokerList = brokerList,
          props = config.customProducerProperties
        )
        producer.send("testMessage")
        val consumer = new KafkaSource[String](TOPIC,
          zookeeperConnect = zookeeperConnectString,
          brokerList = brokerList,
          props = config.customConsumerProperties
        )
        val msg = consumer.subscribe()
        msg should be("testMessage")

//        implicit val d = new StringDeserializer
//        publishStringMessageToKafka(TOPIC, "testMessage")
//        val msg:String = consumeFirstMessageFrom(TOPIC)
//        msg should be ("testMessage")


      }
    } catch {
      case e:RuntimeException => {
        logger.error(e.getMessage, e)
        throw e
      }
    } finally {
      // EmbeddedKafka.stop()
    }
  }

}