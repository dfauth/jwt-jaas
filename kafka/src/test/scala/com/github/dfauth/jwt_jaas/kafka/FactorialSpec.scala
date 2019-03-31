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

        val producer = KafkaProducerWrapper[Payload[Int]](inboundTopic,
          new PayloadSerializer[Int](d => d.toJson),
          brokerList = brokerList,
          zookeeperConnect = zookeeperConnectString,
          props = config.customProducerProperties
        )

        val consumer = new KafkaConsumerWrapper[Result[Int]](outboundTopic,
          new ResultDeserializer[Int](o => o.convertTo[Result[Int]]),
          brokerList = brokerList,
          zookeeperConnect = zookeeperConnectString,
          props = config.customConsumerProperties
        )

        val p = Promise[Int]()
        consumer.subscribe(r => {
          logger.info(s"result: ${r.result}")
          p.success(r.result)
          Future {
            true
          }
        })
        val payload = Payload(3)
        producer.send(payload).onComplete(logSuccess)

        Await.result[Int](p.future, 5.seconds) should be (6)
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





















