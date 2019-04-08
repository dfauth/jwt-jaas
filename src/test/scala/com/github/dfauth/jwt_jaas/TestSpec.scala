package com.github.dfauth.jwt_jaas

import java.security.KeyPair
import java.util.concurrent.{CountDownLatch, TimeUnit}

import akka.Done
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.RouteConcatenation._
import com.github.dfauth.jwt_jaas.common.ContextualPipeline.{adaptFuture, adaptFutureWithContext, wrap}
import com.github.dfauth.jwt_jaas.jwt._
import com.github.dfauth.jwt_jaas.kafka.JsonSupport._
import com.github.dfauth.jwt_jaas.kafka._
import com.github.dfauth.jwt_jaas.rest.RestEndPointServer._
import com.github.dfauth.jwt_jaas.rest.Routes._
import com.github.dfauth.jwt_jaas.rest.TestUtils._
import com.github.dfauth.jwt_jaas.rest.{RestEndPointServer, Tokens}
import com.typesafe.scalalogging.LazyLogging
import io.restassured.http.ContentType
import io.restassured.response.Response
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.hamcrest.Matchers._
import org.scalatest.{FlatSpec, Matchers}
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}


class TestSpec
  extends FlatSpec
    with Matchers
    with EmbeddedKafka
    with LazyLogging {

  val TOPIC = "testTopic"

  val keyPair: KeyPair = KeyPairFactory.createKeyPair("RSA", 2048)
  val jwtBuilder = new JWTBuilder("me",keyPair.getPrivate)
  val jwtVerifier = new JWTVerifier(keyPair.getPublic)

  "putting it all together" should "be simple" in {

    import JsonSupport._

      // REST setup
    val routes:Route = login(authenticateFred) ~ genericPostFutureEndpoint(composeWithFuture)(payloadFormat, resultFormat)

    val endPoint = RestEndPointServer(routes, port = 0)
    val bindingFuture = endPoint.start()
    val binding = Await.result(bindingFuture, 5.seconds)
    implicit val loginEndpoint:String = endPointUrl(binding, "login")

    try {

      // KAFKA setup
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

        val latch = new CountDownLatch(1)
        consumer.subscribe(Utils.compose(jwtVerifier)(u => p => {
          logger.info(s"payload ${p} user: ${u}")
          latch.countDown()
        }))

        // start test
        val userId:String = "fred"
        val password:String = "password"
        val tokens:Tokens = asUser(userId).withPassword(password).login

        val payload = "2"
        val bodyContent:String = Payload(payload).toJson.prettyPrint

        val response:Response = tokens.when.log().all().
          contentType(ContentType.JSON).
          body(bodyContent).
          post(endPointUrl(binding, "endpoint"))

        response.then().log.all.statusCode(200).
          body("result",equalTo( (userFactorCache.cache(userId)*1000).toInt*payload.toInt))

        latch.await(5, TimeUnit.SECONDS)
        latch.getCount should be (0)
      }
    } catch {
      case e:RuntimeException => {
        logger.error(e.getMessage, e)
        throw e
      }
    } finally {
      endPoint.stop(bindingFuture)
      EmbeddedKafka.stop()
    }
  }

  def compose(cache:Map[String, Double]): UserCtx => Payload[String] => Result[Int] = {
    user => wrap(extractPayload).
      map[Int](toInt).
      mapWithContext(lookup(cache)).
      map(doubleToInt).
      map(toResult).
      apply(user)
  }

//  def composeWithFuture: UserCtx => Payload[String] => Future[Done] = {
//    user => p => {
//      val usrCtx = UserContext(user.getToken,Payload("testMessage"))
//      producer.send(usrCtx).onComplete(logSuccess)
//    }.apply(user)

  def composeWithFuture: UserCtx => Payload[String] => Future[Result[Int]] = {
      user => wrap(extractPayload).
      map[Int](toInt).
      map(toFuture).
      mapWithContext(adaptFutureWithContext(lookup(userFactorCache.cache))).
      map(adaptFuture(doubleToInt)).
      map(adaptFuture(toResult)).
      apply(user)
   }

  val extractPayload:Payload[String] => String  = { p =>
    logger.info(s"extractPayload: ${p}")
    p.payload
  }
  val toInt:String => Int = { s =>
    logger.info(s"toInt: ${s}")
    s.toInt
  }
  def lookup(cache:Map[String,Double]):UserCtx => Int => Double  = { user =>
    logger.info(s"lookup: ${user}")
    k => k * cache.getOrElse(user.getUser.getUserId, 1.0)
  }
  val doubleToInt:Double => Int = { d =>
    logger.info(s"doubleToInt: ${d}")
    (d * 1000).toInt
  }
  val toResult:Int => Result[Int] = { i =>
    logger.info(s"toResult: ${i}")
    Result[Int](i)
  }
  def toFuture[T]:T => Future[T] = { t =>
    Future {
      Thread.sleep(500)
      logger.info(s"toFuture: ${t}")
      t
    }
  }

  object userFactorCache {
    val cache = Map("fred" -> 2.0)
  }

  object JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val doneFormat = new RootJsonFormat[Done] {
      def write(done:Done) = JsBoolean(true)
      def read(value: JsValue) = value match {
        case JsTrue => Done
        case x => deserializationError("Expected JsBoolean, but got " + x)
      }
    }
  }
}
