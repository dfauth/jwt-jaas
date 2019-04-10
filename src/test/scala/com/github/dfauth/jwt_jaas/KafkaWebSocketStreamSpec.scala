package com.github.dfauth.jwt_jaas

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest, WebSocketUpgradeResponse}
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives.{handleWebSocketMessages, path}
import akka.http.scaladsl.server.Route
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.{Done, NotUsed}
import com.github.dfauth.jwt_jaas.kafka._
import com.github.dfauth.jwt_jaas.rest.RestEndPointServer
import com.typesafe.scalalogging.LazyLogging
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.scalatest.{FlatSpec, Matchers}

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


  val testPayload: immutable.Iterable[Int] = 0 to 100

  "a websocket " should "be able to stream events from kafka" in {

    import com.github.dfauth.jwt_jaas.kafka.JsonSupport._
    import spray.json._

    try {
      val TOPIC = "subscribe"

      implicit val system = ActorSystem()
      implicit val materializer = ActorMaterializer()

      withRunningKafkaOnFoundPort(EmbeddedKafkaConfig(kafkaPort = 9092, zooKeeperPort = 2181)) { implicit config =>
        val (zookeeperConnectString, brokerList) = connectionProperties(config)

        val payloadSerializer:Payload[Int] => JsValue = _.toJson
        val payloadDeserializer:JsValue => Payload[Int] = _.convertTo[Payload[Int]]

        // create a producer through which we will stream requests to Kafka (this is for etcting purposes only)
        val producerSettings =
          ProducerSettings[String, Payload[Int]](system, new StringSerializer, new JsValueSerializer[Payload[Int]](payloadSerializer))
            .withBootstrapServers(brokerList)

        val adapter = new WebSocketKafkaAdapter(brokerList = brokerList,
          serializer = payloadSerializer,
          deserializer = payloadDeserializer,
          topic = TOPIC)

        val bindingFuture = adapter.start()

        try {
          val binding = Await.result(bindingFuture, 5000.seconds)

          val uri:Uri = RestEndPointServer.endPointUri(binding, "subscribe", "ws") match {
            case Success(uri) => uri
            case Failure(t) => {
              logger.error(t.getMessage, t)
              throw t;
            }
          }

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

          Thread.sleep(5 * 1000)

          val done: Future[Done] =
            Source(testPayload)
              .map(Payload[Int](_))
              .map(value => new ProducerRecord[String, Payload[Int]](TOPIC, value)).map(r => {
              logger.info(s"publish producerRecord: ${r}")
              r
            }).runWith(Producer.plainSink[String, Payload[Int]](producerSettings))

          Thread.sleep(2 * 1000)

          records.toSeq should be (testPayload.toSeq)

        } finally {
          adapter.stop(bindingFuture)
        }
      }
    } finally {
      EmbeddedKafka.stop()
    }
  }

}
