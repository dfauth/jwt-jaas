package com.github.dfauth.jwt_jaas.kafka

import com.typesafe.scalalogging.LazyLogging
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.common.serialization.{Deserializer, Serializer}
import org.scalatest.{FlatSpec, Matchers}
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class FactorialSpec
  extends FlatSpec
    with Matchers
  with EmbeddedKafka
    with LazyLogging {

  "factorials" should "be used as a test function" in {

    factorial(0) should be (0)
    factorial(1) should be (1)
    factorial(2) should be (2)
    factorial(3) should be (6)
    factorial(5) should be (120)
    factorial(13) should be (1932053504)
    factorial(25) should be (2076180480)
  }

  def createKafkaEndPoint[A,B](topicPrefix: String, f: A => B, serializer:Serializer[B], deserializer:Deserializer[A], connectionProperties: (String, String), config:EmbeddedKafkaConfig) = {

    val (zookeeperConnectString, brokerList) = connectionProperties

    val inboundTopic = s"${topicPrefix}.in"
    val outboundTopic = s"${topicPrefix}.out"

    val producer = KafkaProducerWrapper[B](outboundTopic,
      serializer,
      brokerList = brokerList,
      zookeeperConnect = zookeeperConnectString,
      props = config.customProducerProperties
    )

    val consumer = new KafkaConsumerWrapper[A](inboundTopic,
      deserializer,
      brokerList = brokerList,
      zookeeperConnect = zookeeperConnectString,
      props = config.customConsumerProperties
    )

    consumer.subscribe(a => {
      val b = f(a)
      val p = Promise[Boolean]()
      producer.send(b).onComplete {
        case Success(recordMetadata) =>  p.success(true)
        case Failure(t) =>  p.failure(t)
      }
      p.future
    })
    (inboundTopic, outboundTopic)
  }

  def createKafkaBackedFunction[A, B](topicPrefix: String, deserializer:Deserializer[B], serializer:Serializer[A], connectionProperties: (String, String), config:EmbeddedKafkaConfig): A => Future[B] = {

    val inboundTopic = s"${topicPrefix}.in"
    val outboundTopic = s"${topicPrefix}.out"

    val (zookeeperConnectString, brokerList) = connectionProperties

    val producer = KafkaProducerWrapper[A](inboundTopic,
      serializer,
      brokerList = brokerList,
      zookeeperConnect = zookeeperConnectString,
      props = config.customProducerProperties
    )

    val consumer = new KafkaConsumerWrapper[B](outboundTopic,
      deserializer,
      brokerList = brokerList,
      zookeeperConnect = zookeeperConnectString,
      props = config.customConsumerProperties
    )

    val p = Promise[B]()
    val f = (a:A) => {
      producer.send(a).onComplete(logSuccess)
      p.future
    }

    consumer.subscribe(b => {
      p.success(b)
      Future {
        true
      }
    })
    f
  }

  "set up a kafka front end to a function" should "be possible" in {


    import JsonSupport._

    try {

      withRunningKafkaOnFoundPort(EmbeddedKafkaConfig(kafkaPort = 9092, zooKeeperPort = 2181)) { implicit config =>
        val (zookeeperConnectString, brokerList) = connectionProperties(config)

        val (inboundTopic, outboundTopic) = createKafkaEndPoint[Payload[Int], Result[Int]]("factorial",
          (p:Payload[Int]) => Result(factorial(p.payload)),
          new ResultSerializer[Int](d => d.toJson),
          new PayloadDeserializer[Int](o => o.convertTo[Payload[Int]]),
          (zookeeperConnectString, brokerList),
          config)

        val f = createKafkaBackedFunction[Payload[Int], Result[Int]]("factorial",
          new ResultDeserializer[Int](o => o.convertTo[Result[Int]]),
          new PayloadSerializer[Int](d => d.toJson),
          (zookeeperConnectString, brokerList),
          config)

        val payload = Payload(3)
        val p:Future[Result[Int]] = f(payload)

        Await.result[Result[Int]](p, 5.seconds).result should be (6)
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





















