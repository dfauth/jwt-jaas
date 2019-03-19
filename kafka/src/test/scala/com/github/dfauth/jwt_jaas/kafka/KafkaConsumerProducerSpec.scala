package com.github.dfauth.jwt_jaas.kafka

import java.util.concurrent.ArrayBlockingQueue

import com.typesafe.scalalogging.LazyLogging
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise

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
        val producer = KafkaProducerWrapper[String](TOPIC, new StringSerializer, brokerList, zookeeperConnect = zookeeperConnectString, props = config.customProducerProperties)

        val consumer = new KafkaConsumerWrapper[String](TOPIC,
          new StringDeserializer,
          brokerList = brokerList,
          zookeeperConnect = zookeeperConnectString,
          props = config.customConsumerProperties)
        producer.send("testMessage").onComplete(logSuccess)

        consumer.getOneMessage().map(m => m should be ("testMessage")).getOrElse(fail("Oops expecting testMessage"))
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

  val queue = new ArrayBlockingQueue[Tuple2[String, Promise[Boolean]]](2)

  "kafka consumer" should "be able to take a function returning a future indicating if it was successfully processed" in {

    try {

      withRunningKafkaOnFoundPort(EmbeddedKafkaConfig(kafkaPort = 9092, zooKeeperPort = 2181)) { implicit config =>

        val (zookeeperConnectString, brokerList) = connectionProperties(config)

        val producer = KafkaProducerWrapper[String](TOPIC,
          new StringSerializer,
          brokerList = brokerList,
          zookeeperConnect = zookeeperConnectString,
          props = config.customProducerProperties
        )

        val consumer = new KafkaConsumerWrapper[String](TOPIC,
          new StringDeserializer,
          brokerList = brokerList,
          zookeeperConnect = zookeeperConnectString,
          props = config.customConsumerProperties
        )

        consumer.subscribe(v => {
          val p = Promise[Boolean]()
          queue.offer((v, p))
          p.future
        })

        producer.send("testMessage").onComplete(logSuccess)

        val (v, t) = queue.take()
        logger.info(s"${v}")
        v should be ("testMessage")
        t.success(true)
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

  "a kafka consumer function" should "be able to indicate failure to process via its future return value and have the message replayed" in {

    try {

      withRunningKafkaOnFoundPort(EmbeddedKafkaConfig(kafkaPort = 9092, zooKeeperPort = 2181)) { implicit config =>

        val (zookeeperConnectString, brokerList) = connectionProperties(config)

        val producer = KafkaProducerWrapper[String](TOPIC,
          new StringSerializer,
          brokerList = brokerList,
          zookeeperConnect = zookeeperConnectString,
          props = config.customProducerProperties
        )

        val consumer = new KafkaConsumerWrapper[String](TOPIC,
          new StringDeserializer,
          brokerList = brokerList,
          zookeeperConnect = zookeeperConnectString,
          props = config.customConsumerProperties
        )

        consumer.subscribe(v => {
          val p = Promise[Boolean]()
          queue.offer((v, p))
          p.future
        })
        producer.send("testMessage").onComplete(logSuccess)

        val (v, t) = queue.take()
        logger.info(s"WOOZ received it once: ${v}")
        v should be ("testMessage")
        t.failure(new RuntimeException("Oops"))

        // close the consumer
        consumer.stop

        // create a new consumer
        val consumer1 = new KafkaConsumerWrapper[String](TOPIC,
          new StringDeserializer,
          brokerList = brokerList,
          zookeeperConnect = zookeeperConnectString,
          props = config.customConsumerProperties
        )
        consumer1.subscribe(v => {
          val p = Promise[Boolean]()
          queue.offer((v, p))
          p.future
        })

        // wait on redelivery
        val (v1, t1) = queue.take()
        logger.info(s"WOOZ received it a second time: ${v1}")
        v1 should be ("testMessage")
        t1.success(true)
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