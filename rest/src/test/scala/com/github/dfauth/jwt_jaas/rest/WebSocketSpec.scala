package com.github.dfauth.jwt_jaas.rest

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.server.Directives.{handleWebSocketMessages, path}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

class WebSocketSpec extends FlatSpec with Matchers with LazyLogging {

  val host = "localhost"
  val port = 0

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  "a websocket endpoint" should "handle incomin subscriptions" in {

    val websocketRoute:Route =
        path("subscribe") {
            handleWebSocketMessages(subscribeFlow)
        }

    val endPoint = RestEndPointServer(websocketRoute, host, port)
    val bindingFuture = endPoint.start()

    val binding = Await.result(bindingFuture, 5000.seconds)
    val uri:Uri = RestEndPointServer.endPointUri(binding, "subscribe", "ws") match {
      case Success(uri) => uri
      case Failure(t) => {
        logger.error(t.getMessage, t)
        throw t;
      }
    }

    try {
      // Future[Done] is the materialized value of Sink.foreach,
      // emitted when the stream completes
      val incoming: Sink[Message, Future[Done]] =
      Sink.foreach[Message] {
        case message: TextMessage.Strict =>
          logger.info(s"received: ${message.text}")
        case message: TextMessage.Streamed =>
          message.textStream.to(Sink.foreach(t => logger.info(s" testStream: ${t}"))).run()
          // logger.info(s"received: ${message.textStream}")
        case x => logger.info(s"received something else: ${x}")
      }
      // send this as a message over the WebSocket
      val outgoing = Source.single(TextMessage("hello world!"))
      // flow to use (note: not re-usable!)
      val webSocketFlow:Flow[Message, Message, Future[WebSocketUpgradeResponse]] = Http().webSocketClientFlow(WebSocketRequest(uri))
      // the materialized value is a tuple with
      // upgradeResponse is a Future[WebSocketUpgradeResponse] that
      // completes or fails when the connection succeeds or fails
      // and closed is a Future[Done] with the stream completion from the incoming sink
      val (upgradeResponse, closed) =
      outgoing
        .viaMat(webSocketFlow)(Keep.right) // keep the materialized Future[WebSocketUpgradeResponse]
        .toMat(incoming)(Keep.both) // also keep the Future[Done]
        .run()
      // just like a regular http request we can access response status which is available via upgrade.response.status
      // status code 101 (Switching Protocols) indicates that server support WebSockets
      val connected = upgradeResponse.flatMap { upgrade =>
        if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
          Future.successful(Done)
        } else {
          throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
        }
      }
      // in a real application you would not side effect here
      Thread.sleep(10 * 1000)
      connected.onComplete(t => logger.info("connection complete"))
      closed.foreach(_ => logger.info("closed"))
    } finally {
      endPoint.stop(bindingFuture)
    }
  }

  val subscribeFlow =
    Flow[Message]
      .mapConcat {
        // we match but don't actually consume the text message here,
        // rather we simply stream it back as the tail of the response
        // this means we might start sending the response even before the
        // end of the incoming message has been received
        case tm: TextMessage => {
          logger.info(s"received TextMessage: ${tm}")
          val out = TextMessage(Source.single("Hello ") ++ tm.textStream) :: Nil
          logger.info(s"reply with: ${out}")
          out
        }
        case bm: BinaryMessage =>
          // ignore binary messages but drain content to avoid the stream being clogged
          bm.dataStream.runWith(Sink.ignore)
          Nil
      }}


