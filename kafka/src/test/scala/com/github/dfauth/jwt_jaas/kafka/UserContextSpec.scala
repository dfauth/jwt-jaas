package com.github.dfauth.jwt_jaas.kafka

import java.util
import java.util.concurrent.ArrayBlockingQueue

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.typesafe.scalalogging.LazyLogging
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.common.serialization.{Deserializer, Serializer}
import org.scalatest.{FlatSpec, Matchers}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat, _}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

class UserContextSpec
  extends FlatSpec
    with Matchers
    with EmbeddedKafka
    with LazyLogging {

  val TOPIC = "testTopic"

  "user context" should "be something" in {

    try {

      withRunningKafkaOnFoundPort(EmbeddedKafkaConfig(kafkaPort = 9092, zooKeeperPort = 2181)) { implicit config =>
        val (zookeeperConnectString, brokerList) = connectionProperties(config)

        val producer = KafkaProducerWrapper[Payload[String]](TOPIC,
          StringPayloadSerializer(),
          brokerList = brokerList,
          zookeeperConnect = zookeeperConnectString,
          props = config.customProducerProperties
        )

        val consumer = new KafkaConsumerWrapper[Payload[String]](TOPIC,
          new StringPayloadDeserializer,
          brokerList = brokerList,
          zookeeperConnect = zookeeperConnectString,
          props = config.customConsumerProperties
        )

        val wrapper = Utils.wrap[Payload[String],Result[Int]]((p:Payload[String]) => Result(p.payload.toInt))
        consumer.subscribe(wrapper.function)
        producer.send(Payload("testMessage")).onComplete(logSuccess)

        // wait on redelivery
        val v1 = wrapper.take()
        logger.info(s"WOOZ received : ${v1}")
        v1.payload should be ("testMessage")
        wrapper.success(true)
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

object Utils {
  def wrap[A,B](f: A => B) = {
    Wrapper(f)
  }
}

case class Wrapper[A,B](f:A=>B) {

  val queue = new ArrayBlockingQueue[Tuple2[A,Promise[Boolean]]](1)

  def success(bool: Boolean): Unit = {}

  def take():A = queue.take()._1

  def function: A => Future[Boolean] = (a:A) => {
    val p = Promise[Boolean]()
    queue.offer((a, p))
    p.future
  }

}

case class Payload[T](payload:T)
case class Result[T](result:T)

object JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val payloadFormat:RootJsonFormat[Payload[String]] = jsonFormat1(Payload[String])
  implicit val resultFormat:RootJsonFormat[Result[Int]] = jsonFormat1(Result[Int])
}

case class StringPayloadDeserializer() extends PayloadDeserializer[String] {
  override def deserialize(topic: String, data: Array[Byte]): Payload[String] = JsonSupport.payloadFormat.read(data.toJson)
}

abstract class PayloadDeserializer[T]() extends Deserializer[Payload[T]] {
  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {}

  override def close(): Unit = {}
}

case class StringPayloadSerializer() extends PayloadSerializer[String] {
  override def serialize(topic: String, data: Payload[String]): Array[Byte] = JsonSupport.payloadFormat.write(data).prettyPrint.getBytes
}

abstract class PayloadSerializer[T]() extends Serializer[Payload[T]] {

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {}

  override def close(): Unit = {}
}