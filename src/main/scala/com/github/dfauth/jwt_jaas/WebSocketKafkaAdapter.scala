package com.github.dfauth.jwt_jaas

import java.security.PublicKey

import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives.{handleWebSocketMessages, path}
import akka.http.scaladsl.server.Route
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, RunnableGraph, Sink, SinkQueue, SinkQueueWithCancel, Source, SourceQueue}
import com.github.dfauth.jwt_jaas.jwt.{JWTVerifier, KeyPairFactory}
import com.github.dfauth.jwt_jaas.kafka.JsValueDeserializer
import com.github.dfauth.jwt_jaas.rest.TokenValidator
import com.github.dfauth.jwt_jaas.rest.MyDirectives.authenticate
import com.github.dfauth.jwt_jaas.rest.Routes.jwtVerifier
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.common.serialization.StringDeserializer
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
                               publicKey:PublicKey,
                               serializer:T => JsValue) extends LazyLogging with TokenValidator {

  implicit val system = ActorSystem("webSocketKafkaAdapter")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val jwtVerifier = new JWTVerifier(publicKey)

  val websocketRoute:Route =
    authenticate { u =>
      path(endpoint) {
        handleWebSocketMessages(subscribeFlow(brokerList))
      }
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

    lazy val subscription = Subscriptions.topics(topic)

    val consumerSettings =
      ConsumerSettings[String, T](system, new StringDeserializer, new JsValueDeserializer[T](deserializer))
        .withBootstrapServers(brokerList)
        .withGroupId(java.util.UUID.randomUUID.toString)

    val source:Source[TextMessage, Any] = Consumer.plainSource(consumerSettings, subscription).
      mapAsync[String](1)(r => Future{serializer(r.value()).prettyPrint}).
      map[TextMessage](s => TextMessage(s)).buffer(1024, OverflowStrategy.dropHead)

    Flow.fromSinkAndSource(Sink.ignore, source)
  }
}
