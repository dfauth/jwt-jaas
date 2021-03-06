package com.github.dfauth.jwt_jaas.kafka

import java.util
import java.util.concurrent.ArrayBlockingQueue

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.github.dfauth.jwt_jaas.jwt.JWTVerifier.TokenAuthentication.{Failure, Success}
import com.github.dfauth.jwt_jaas.jwt.{JWTVerifier, User}
import com.typesafe.scalalogging.LazyLogging
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.common.serialization.Deserializer
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
  val TOKEN = "blahToken"


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
          UserContextSerializer(d => d.toJson),
          brokerList = brokerList,
          zookeeperConnect = zookeeperConnectString,
          props = config.customProducerProperties
        )

        val consumer = new KafkaConsumerWrapper[UserContext[Payload[String]]](TOPIC,
          new UserContextDeserializer(d => d.convertTo[UserContext[Payload[String]]]),
          brokerList = brokerList,
          zookeeperConnect = zookeeperConnectString,
          props = config.customConsumerProperties
        )

        val wrapper = Utils.wrap[UserContext[Payload[String]],UserContext[Result[Int]]]((uc:UserContext[Payload[String]]) => UserContext(uc.token,Result(uc.payload.payload.toInt)))
        consumer.subscribe(wrapper.function)
        val usrCtx = UserContext(TOKEN,Payload("testMessage"))
        producer.send(usrCtx).onComplete(logSuccess)

        val v = wrapper.take()
        logger.info(s"WOOZ received : ${v}")
        v should be (usrCtx)
        v.token should be (TOKEN)
        v.payload should be (Payload("testMessage"))
        v.payload.payload should be ("testMessage")
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

    {
      val ref = new UserContext[Payload[String]]("tokenString", new Payload[String]("hello"))
      val serialized = new UserContextSerializer[Payload[String]](d => d.toJson).serialize(TOPIC, ref)
      val result = new UserContextDeserializer[Payload[String]](d => d.convertTo[UserContext[Payload[String]]]).deserialize(TOPIC,serialized)
      result should be (ref)
    }
  }
}

object Utils extends LazyLogging {
  def wrap[A,B](f: A => B) = {
    Wrapper(f)
  }
  def compose[T](jwtVerifier:JWTVerifier)(f:User => Payload[T] => Unit):UserContext[Payload[T]] => Future[Boolean] = {
    userCtx => {
      jwtVerifier.authenticateToken(userCtx.token, jwtVerifier.asUser) match {
        case s:Success[User] => {
          f(s.getPayload)(userCtx.payload)
          Future {
            true
          }
        }
        case f:Failure[User] => {
          logger.info(s" token verification failed; ${f.getCause()}")
          Future {
            false
          }
        }
      }
    }
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

class PayloadDeserializer[T](f:JsValue => Payload[T]) extends JsValueDeserializer[Payload[T]](f)

case class UserContextDeserializer[T](f:JsValue => UserContext[T]) extends JsValueDeserializer[UserContext[T]](f)

case class ResultDeserializer[T](f:JsValue => Result[T]) extends JsValueDeserializer[Result[T]](f)

case class UserContextSerializer[T](f:UserContext[T] => JsValue) extends JsValueSerializer[UserContext[T]](f)

case class PayloadSerializer[T](f:Payload[T] => JsValue) extends JsValueSerializer[Payload[T]](f)

case class ResultSerializer[T](f:Result[T] => JsValue) extends JsValueSerializer[Result[T]](f)

