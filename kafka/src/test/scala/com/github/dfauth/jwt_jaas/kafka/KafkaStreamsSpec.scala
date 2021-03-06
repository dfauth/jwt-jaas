package com.github.dfauth.jwt_jaas.kafka

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.{ConsumerSettings, ProducerSettings, Subscription, Subscriptions}
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.scalalogging.LazyLogging
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.mutable.ListBuffer
import scala.collection.{immutable, mutable}
import scala.concurrent.Future

class KafkaStreamsSpec
  extends FlatSpec
    with Matchers
    with EmbeddedKafka
    with LazyLogging {

  val TOPIC = "testTopic"

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val testPayload: immutable.Iterable[Int] = 0 to 100

  "akka streams" should "allow objects to be streamed to and from kafka preserving the order" in {

    try {

      import JsonSupport._
      import spray.json._

      withRunningKafkaOnFoundPort(EmbeddedKafkaConfig(kafkaPort = 9092, zooKeeperPort = 2181)) { implicit config =>
        val (zookeeperConnectString, brokerList) = connectionProperties(config)
        val producerSettings =
          ProducerSettings[String, Payload[Int]](system, new StringSerializer, new JsValueSerializer[Payload[Int]]((p:Payload[Int]) => p.toJson))
            .withBootstrapServers(brokerList)

        lazy val subscription = Subscriptions.topics(TOPIC)

        val consumerSettings =
          ConsumerSettings[String, Payload[Int]](system, new StringDeserializer, new JsValueDeserializer[Payload[Int]](o => o.convertTo[Payload[Int]]))
            .withBootstrapServers(brokerList)
          .withGroupId(java.util.UUID.randomUUID.toString)

        val records:mutable.ListBuffer[Int] = ListBuffer.empty[Int]
        Consumer.plainSource(consumerSettings, subscription).runWith(Sink.foreach { t =>
          records += t.value().payload
        })

        Thread.sleep(5 * 1000)

        val done: Future[Done] =
          Source(testPayload)
            .map(Payload[Int](_))
            .map(value => new ProducerRecord[String, Payload[Int]](TOPIC, value))
            .runWith(Producer.plainSink[String, Payload[Int]](producerSettings))

        Thread.sleep(5 * 1000)

        records.toList should be (testPayload.toList)
      }
    } finally {
      // EmbeddedKafka.stop()
    }
  }

}























