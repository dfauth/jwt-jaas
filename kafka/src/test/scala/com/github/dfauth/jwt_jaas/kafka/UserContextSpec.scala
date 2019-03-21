package com.github.dfauth.jwt_jaas.kafka

import java.util
import java.util.concurrent.ArrayBlockingQueue

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.typesafe.scalalogging.LazyLogging
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.common.serialization.{Deserializer, Serializer}
import org.scalatest.{FlatSpec, Matchers}
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

class UserContextSpec
  extends FlatSpec
    with Matchers
    with EmbeddedKafka
    with LazyLogging {

  val TOPIC = "testTopic"


  "jsonsupport" should "support round trip" in {

    import JsonSupport._

    val payload: Payload[String] = new Payload("test")
    val bytes = new PayloadSerializer[String](d => d.toJson).serialize(TOPIC, payload)
    val result = new PayloadDeserializer[String](o => o.convertTo[Payload[String]]).deserialize(TOPIC, bytes)
    result should be (payload)
  }

  "user context" should "be something" in {

    import JsonSupport._

    try {

      withRunningKafkaOnFoundPort(EmbeddedKafkaConfig(kafkaPort = 9092, zooKeeperPort = 2181)) { implicit config =>
        val (zookeeperConnectString, brokerList) = connectionProperties(config)

        val producer = KafkaProducerWrapper[Payload[String]](TOPIC,
          new PayloadSerializer[String](d => d.toJson),
          brokerList = brokerList,
          zookeeperConnect = zookeeperConnectString,
          props = config.customProducerProperties
        )

        val consumer = new KafkaConsumerWrapper[Payload[String]](TOPIC,
          new PayloadDeserializer[String](o => o.convertTo[Payload[String]]),
          brokerList = brokerList,
          zookeeperConnect = zookeeperConnectString,
          props = config.customConsumerProperties
        )

        val wrapper = Utils.wrap[Payload[String],Result[Int]]((p:Payload[String]) => Result(p.payload.toInt))
        consumer.subscribe(wrapper.function)
        val payload = Payload("testMessage")
        producer.send(payload).onComplete(logSuccess)

        val v1 = wrapper.take()
        logger.info(s"WOOZ received : ${v1}")
        v1 should be (payload)
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

  "user context" should "include the user token" in {

    import JsonSupport._

    try {

      withRunningKafkaOnFoundPort(EmbeddedKafkaConfig(kafkaPort = 9092, zooKeeperPort = 2181)) { implicit config =>
        val (zookeeperConnectString, brokerList) = connectionProperties(config)

        val producer = KafkaProducerWrapper[UserContext[Payload[String]]](TOPIC,
          UserContextSerializer(new PayloadSerializer[String](d => d.toJson)),
          brokerList = brokerList,
          zookeeperConnect = zookeeperConnectString,
          props = config.customProducerProperties
        )

        val consumer = new KafkaConsumerWrapper[UserContext[Payload[String]]](TOPIC,
          new UserContextDeserializer(new PayloadDeserializer[String](o => o.convertTo[Payload[String]])),
          brokerList = brokerList,
          zookeeperConnect = zookeeperConnectString,
          props = config.customConsumerProperties
        )

        val wrapper = Utils.wrap[UserContext[Payload[String]],UserContext[Result[Int]]]((uc:UserContext[Payload[String]]) => UserContext(uc.token,Result(uc.payload.payload.toInt)))
        consumer.subscribe(wrapper.function)
        val payload = UserContext("blahToken",Payload("testMessage"))
        producer.send(payload).onComplete(logSuccess)

        val v1 = wrapper.take()
        logger.info(s"WOOZ received : ${v1}")
        v1 should be (payload)
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

  "json format" should "allow round trip" in {

    import JsonSupport._

    {
      val ref = new Payload[String]("hello")
      val serialized = ref.toJson.prettyPrint.getBytes
      val result = JsonParser(serialized).asJsObject.convertTo[Payload[String]]
      result should be (ref)
    }

    {
      val ref = new Result[Int](2)
      val serialized = ref.toJson.prettyPrint.getBytes
      val result = JsonParser(serialized).asJsObject.convertTo[Result[Int]]
      result should be (ref)
    }

    {
      val ref = new UserContext[Payload[String]]("blah", new Payload[String]("hello"))
      val serialized = ref.toJson.prettyPrint.getBytes
      val result = JsonParser(serialized).asJsObject.convertTo[UserContext[Payload[String]]]
      result should be (ref)
    }

    {
      val ref = new Payload[String]("hello")
      val serialized = new PayloadSerializer[String](d => d.toJson).serialize(TOPIC, ref)
      val result = new PayloadDeserializer[String](o => o.convertTo[Payload[String]]).deserialize(TOPIC,serialized)
      result should be (ref)
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
  val p = Promise[Boolean]()

  def success(b: Boolean): Unit = p.success(b)

  def failure(t: Throwable): Unit = p.failure(t)

  def take():A = queue.take()._1

  def function: A => Future[Boolean] = (a:A) => {
    queue.offer((a, p))
    p.future
  }

}

case class UserContext[T](token:String, payload:T)
case class Payload[T](payload:T)
case class Result[T](result:T)

object JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit def usCtxFormat[T:JsonFormat]:RootJsonFormat[UserContext[T]] = jsonFormat2(UserContext.apply[T])
  implicit def payloadFormat[T:JsonFormat]:RootJsonFormat[Payload[T]] = jsonFormat1(Payload.apply[T])
  implicit def resultFormat[T:JsonFormat]:RootJsonFormat[Result[T]] = jsonFormat1(Result.apply[T])
}

class PayloadDeserializer[T](f:JsValue => Payload[T]) extends Deserializer[Payload[T]] {
  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {}

  override def close(): Unit = {}

  override def deserialize(topic: String, data: Array[Byte]): Payload[T] = {
    val jsObj: JsValue = JsonParser(data).asJsObject
    f(jsObj)
  }
}

case class UserContextSerializer[T](nested:Serializer[T]) extends Serializer[UserContext[T]] {
  override def serialize(topic: String, data: UserContext[T]): Array[Byte] = ???
//    val result:Array[Byte] = nested.serialize(topic, data.payload)
//    JsonSupport.usCtxFormat.write(re).prettyPrint.getBytes
  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {}

  override def close(): Unit = {}
}

case class UserContextDeserializer[T](nested:Deserializer[T]) extends Deserializer[UserContext[T]] {
  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {}

  override def close(): Unit = {}

  override def deserialize(topic: String, data: Array[Byte]): UserContext[T] = ???
}

class PayloadSerializer[T](f:Payload[T] => JsValue) extends Serializer[Payload[T]] {

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {}

  override def close(): Unit = {}

  override def serialize(topic: String, data: Payload[T]): Array[Byte] = {
    f(data).prettyPrint.getBytes
  }
}