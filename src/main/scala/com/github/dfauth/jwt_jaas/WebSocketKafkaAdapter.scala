package com.github.dfauth.jwt_jaas

import akka.actor.ActorSystem
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives.{complete, get, handleWebSocketMessages, path}
import akka.http.scaladsl.server.Route
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}
import com.github.dfauth.jwt_jaas.kafka.{JsValueDeserializer, JsValueSerializer}
import com.github.dfauth.jwt_jaas.rest.RestEndPointServer
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import spray.json.JsValue

import scala.concurrent.Future

object WebSocketKafkaAdapter {

  val statusRoute:Route = path("status") {
    get {
      complete(HttpEntity(ContentTypes.`application/json`, """{"ok": true}"""))
    }
  }

}

class WebSocketKafkaAdapter[T](hostname:String = "localhost",
                               port:Int = 8080,
                               brokerList:String = "localhost:9092",
                               topic:String = "subscribe",
                               deserializer:JsValue => T,
                               serializer:T => JsValue) extends RestEndPointServer(WebSocketKafkaAdapter.statusRoute, hostname, port) {

  val websocketRoute:Route =
    path("subscribe") {
      handleWebSocketMessages(subscribeFlow(brokerList))
    }

  def start():Future[ServerBinding] = start(websocketRoute)

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

