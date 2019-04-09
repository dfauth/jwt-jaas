package com.github.dfauth.jwt_jaas

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives.{handleWebSocketMessages, path}
import akka.http.scaladsl.server.Route
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}
import com.github.dfauth.jwt_jaas.kafka.{JsValueDeserializer}
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.common.serialization.{StringDeserializer}
import spray.json.JsValue

import scala.concurrent.Future

object WebSocketKafkaAdapter {


}

class WebSocketKafkaAdapter[T](hostname:String = "localhost",
                               port:Int = 8080,
                               brokerList:String = "localhost:9092",
                               topic:String = "subscribe",
                               endpoint:String = "subscribe",
                               deserializer:JsValue => T,
                               serializer:T => JsValue) extends LazyLogging {

  implicit val system = ActorSystem("webSocketKafkaAdapter")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val websocketRoute:Route =
    path(endpoint) {
      handleWebSocketMessages(subscribeFlow(brokerList))
    }

  def start():Future[ServerBinding] = {
    Http().bindAndHandle(websocketRoute, hostname, port)
  }

  def stop(bindingFuture:Future[ServerBinding]):Unit = {
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => {
      system.terminate()
      logger.info("system terminated")
    }) // and shutdown when done
  }

  def subscribeFlow(brokerList:String): Flow[Message, Message, Any] = {

    import spray.json._
    import JsonSupport._

    lazy val subscription = Subscriptions.topics(topic)

    val consumerSettings =
      ConsumerSettings[String, T](system, new StringDeserializer, new JsValueDeserializer[T](deserializer))
        .withBootstrapServers(brokerList)
        .withGroupId(java.util.UUID.randomUUID.toString)

    val source = Consumer.plainSource(consumerSettings, subscription).map[String](r => serializer(r.value()).prettyPrint).map[TextMessage](s => TextMessage(s))

    return Flow.fromSinkAndSource(Sink.foreach(i => {
      logger.info(s"subscribeFlow: ${i}")
    }), source)
  }
}
