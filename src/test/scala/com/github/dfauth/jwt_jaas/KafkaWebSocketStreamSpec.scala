package com.github.dfauth.jwt_jaas

import java.security.KeyPair

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest, WebSocketUpgradeResponse}
import akka.http.scaladsl.model.{StatusCodes, Uri}

import scala.concurrent.ExecutionContext.Implicits.global
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.{Done, NotUsed}
import com.github.dfauth.jwt_jaas.jwt.KeyPairFactory
import com.github.dfauth.jwt_jaas.kafka._
import com.github.dfauth.jwt_jaas.rest.{AuthenticationService, RestEndPointServer, TestUtils, Tokens}
import com.github.dfauth.jwt_jaas.rest.TestUtils.asUser
import com.typesafe.scalalogging.LazyLogging
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.scalatest.{FlatSpec, Matchers}
import spray.json.JsonParser

import scala.collection.immutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

class KafkaWebSocketStreamSpec
  extends FlatSpec
    with Matchers
    with EmbeddedKafka
    with LazyLogging {

  import com.github.dfauth.jwt_jaas.kafka.JsonSupport._
  import spray.json._

  val testPayload: immutable.Iterable[Int] = 0 to 100

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  "a websocket " should "be able to stream events from kafka" in {

    try {
      val TOPIC = "subscribe"

      val keyPair: KeyPair = KeyPairFactory.createKeyPair("RSA", 2048)

      withRunningKafkaOnFoundPort(EmbeddedKafkaConfig(kafkaPort = 9092, zooKeeperPort = 2181)) { implicit config =>
        val (zookeeperConnectString, brokerList) = connectionProperties(config)

        val payloadSerializer:Payload[Int] => JsValue = _.toJson
        val payloadDeserializer:JsValue => Payload[Int] = _.convertTo[Payload[Int]]

        // create a producer through which we will stream requests to Kafka (this is for testing purposes only)
        val producerSettings =
          ProducerSettings[String, Payload[Int]](system, new StringSerializer, new JsValueSerializer[Payload[Int]](payloadSerializer))
            .withBootstrapServers(brokerList)

        // create an authentication service
        val authService = new AuthenticationService(port = 0, f = TestUtils.authenticateFred)
        val authBindingFuture = authService.start()

        // create the websocket adapter
        val adapter = new WebSocketKafkaAdapter(brokerList = brokerList,
          serializer = payloadSerializer,
          deserializer = payloadDeserializer,
          publicKey = authService.getPublicKey,
          topic = TOPIC)

        val webSocketBindingFuture = adapter.start()

        try {
          // wait for start up
          val webSocketBinding = Await.result(webSocketBindingFuture, 5000.seconds)
          val authBinding = Await.result(authBindingFuture, 5000.seconds)

          implicit val endPoint = RestEndPointServer.endPointUrl(authBinding, "login")

          // login as fred
          val userId:String = "fred"
          val password:String = "password"
          val tokens:Tokens = asUser(userId).withPassword(password).login

//          val uri:Uri = RestEndPointServer.endPointUri(binding, "subscribe", "ws") match {
//            case Success(uri) => uri
//            case Failure(t) => {
//              logger.error(t.getMessage, t)
//              throw t;
//            }
//          }

//          val records = connect(uri)
//          val records1 = connect(uri)

          Thread.sleep(5 * 1000)

          val done: Future[Done] =
            Source(testPayload)
              .map(Payload[Int](_))
              .map(value => new ProducerRecord[String, Payload[Int]](TOPIC, value)).map(r => {
              logger.info(s"publish producerRecord: ${r}")
              r
            }).runWith(Producer.plainSink[String, Payload[Int]](producerSettings))

          Thread.sleep(2 * 1000)

//          records should be (testPayload.toSeq)
//          records1 should be (testPayload.toSeq)

        } finally {
          adapter.stop(webSocketBindingFuture)
          authService.stop(authBindingFuture)
        }
      }
    } finally {
      EmbeddedKafka.stop()
    }
  }

  def connect(uri: Uri):ListBuffer[Int] = {

    val webSocketFlow:Flow[Message, Message, Future[WebSocketUpgradeResponse]] = Http().webSocketClientFlow(WebSocketRequest(uri))

    val messageSource: Source[Message, ActorRef] =
      Source.actorRef[TextMessage.Strict](bufferSize = 10, OverflowStrategy.fail)

    val records = new ListBuffer[Int]()
    val messageSink: Sink[Message, NotUsed] =
      Flow[Message]
        .map(_ match {
          case t:TextMessage.Strict => t.text
        }
        ).map( t => {
        val p = JsonParser(t).asJsObject.convertTo[Payload[Int]].payload
        p
      }
      ).to(Sink.foreach(i => {
        records += i
        logger.info(s"i is ${i}")
      }))

    val ((ws, upgradeResponse), closed) =
      messageSource
        .viaMat(webSocketFlow)(Keep.both)
        .toMat(messageSink)(Keep.both)
        .run()

    val connected = upgradeResponse.flatMap { upgrade =>
      if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
        Future.successful(Done)
      } else {
        throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
      }
    }
    records
  }

}
