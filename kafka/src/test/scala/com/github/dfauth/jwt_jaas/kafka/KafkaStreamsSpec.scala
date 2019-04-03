package com.github.dfauth.jwt_jaas.kafka

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import com.typesafe.scalalogging.LazyLogging
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Future

class KafkaStreamsSpec
  extends FlatSpec
    with Matchers
    with EmbeddedKafka
    with LazyLogging {

  val TOPIC = "testTopic"

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  "akka streams" should "be possible" in {

    try {

      withRunningKafkaOnFoundPort(EmbeddedKafkaConfig(kafkaPort = 9092, zooKeeperPort = 2181)) { implicit config =>
        val (zookeeperConnectString, brokerList) = connectionProperties(config)
        val producerSettings =
          ProducerSettings(system, new StringSerializer, new StringSerializer)
            .withBootstrapServers(brokerList)

        val done: Future[Done] =
          Source(1 to 100)
            .map(_.toString)
            .map(value => new ProducerRecord[String, String](TOPIC, value))
          .map(r => {
            logger.info("producerRecord: "+r)
            r
          })
            .runWith(Producer.plainSink(producerSettings))

        Thread.sleep(10 * 1000)
      }
    } finally {
      // EmbeddedKafka.stop()
    }
  }

}























