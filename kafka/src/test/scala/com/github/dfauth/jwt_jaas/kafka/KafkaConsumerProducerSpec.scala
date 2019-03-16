package com.github.dfauth.jwt_jaas.kafka

import com.typesafe.scalalogging.LazyLogging
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.common.serialization.StringDeserializer
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.JavaConverters._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class KafkaConsumerProducerSpec
  extends FlatSpec
    with Matchers
    with EmbeddedKafka
    with LazyLogging {

  val TOPIC = "testTopic"

  "kafka consumer and producers" should "work be able to exchange messages" in {

    try {

      withRunningKafkaOnFoundPort(EmbeddedKafkaConfig(kafkaPort = 9092, zooKeeperPort = 2181)) { implicit config =>
        val zookeeperConnectString = "localhost:" + config.zooKeeperPort
        val brokerList = "localhost:" + config.kafkaPort
        val producer = KafkaProducerWrapper[String](TOPIC, brokerList, props = config.customProducerProperties)

        val consumer = new KafkaConsumer[String](TOPIC,new StringDeserializer,
          zookeeperConnect = zookeeperConnectString,
          brokerList = brokerList,
          props = config.customConsumerProperties
        )
        producer.send("testMessage").onComplete {
          case Success(r) => {
            logger.info(s"success: ${r}")
          }
          case Failure(f) => {
            logger.error(f.getMessage, f)
          }
        }

        consumer.getOneMessage().map(_ should be ("testMessage")).getOrElse(fail("Oops expecting testMessage"))
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