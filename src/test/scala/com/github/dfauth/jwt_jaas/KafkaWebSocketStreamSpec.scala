package com.github.dfauth.jwt_jaas

import java.security.KeyPair

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest, WebSocketUpgradeResponse}
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.{Done, NotUsed}
import com.github.dfauth.jwt_jaas.discovery.ApiGateway
import com.github.dfauth.jwt_jaas.jwt.KeyPairFactory
import com.github.dfauth.jwt_jaas.kafka._
import com.github.dfauth.jwt_jaas.rest.TestUtils.asUser
import com.github.dfauth.jwt_jaas.rest.{AuthenticationService, RestEndPointServer, TestUtils, Tokens}
import com.typesafe.scalalogging.LazyLogging
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.immutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

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

      val apiGateway = new ApiGateway(port = 0)

      withRunningKafkaOnFoundPort(EmbeddedKafkaConfig(kafkaPort = 9092, zooKeeperPort = 2181)) { implicit config =>
        val (zookeeperConnectString, brokerList) = connectionProperties(config)

        val payloadSerializer:Payload[Int] => JsValue = _.toJson
        val payloadDeserializer:JsValue => Payload[Int] = _.convertTo[Payload[Int]]

        // create a producer through which we will stream requests to Kafka (this is for testing purposes only)
        val producerSettings =
          ProducerSettings[String, Payload[Int]](system, new StringSerializer, new JsValueSerializer[Payload[Int]](payloadSerializer))
            .withBootstrapServers(brokerList)

        // create an authentication service behind the apiGateway
        val authService = apiGateway.bind("login", new AuthenticationService(port = 0, f = TestUtils.authenticateFred))

        // create the websocket adapter behind the apiGateway
        val adapter = apiGateway.bind("subscribe", new WebSocketKafkaAdapter(brokerList = brokerList, port = 0,
            serializer = payloadSerializer,
            deserializer = payloadDeserializer,
            publicKey = authService.getPublicKey,
            topic = TOPIC))

        val apiGatewayBindingFuture = apiGateway.start()

        try {
          // wait for start up
          apiGateway.startServices(5.seconds)

          val apiGatewayBinding = Await.result(apiGatewayBindingFuture, 5.seconds)

          implicit val endPoint = RestEndPointServer.endPointUrl(apiGatewayBinding, "login")

          // login as fred
          val userId:String = "fred"
          val password:String = "password"
          val tokens:Tokens = asUser(userId).withPassword(password).login

          import RestEndPointServer._

          val records = new ListBuffer[Int]()
          val webSocketEndpoint = tokens.webSocket(endPointURI(apiGatewayBinding, "subscribe", "ws"), a => {
            val p = JsonParser(a).asJsObject.convertTo[Payload[Int]]
            records += p.payload
          })

          Thread.sleep(5 * 1000)

          implicit val system = ActorSystem()
          implicit val materializer = ActorMaterializer()

          val done: Future[Done] =
            Source(testPayload)
              .map(Payload[Int](_))
              .map(value => new ProducerRecord[String, Payload[Int]](TOPIC, value)).map(r => {
              logger.info(s"publish producerRecord: ${r}")
              r
            }).runWith(Producer.plainSink[String, Payload[Int]](producerSettings))

          Thread.sleep(2 * 1000)

          records should be (testPayload.toSeq)

        } finally {
          apiGateway.startServices()
          apiGateway.stop(apiGatewayBindingFuture)
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
