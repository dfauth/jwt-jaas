package com.github.dfauth.jwt_jaas

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future

object RestEndPointServer {
}

case class RestEndPointServer(route:Route, hostname:String = "localhost", port:Int = 8080) extends LazyLogging {

  def endPointUrl(str: String):String = s"http://${hostname}:${port}/${str}"


  implicit val system = ActorSystem("my-system")
  implicit val materializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher

  def start():Future[ServerBinding] = Http().bindAndHandle(route, hostname, port)

  def stop(bindingFuture:Future[ServerBinding]):Unit = {
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => {
      system.terminate()
      logger.info("system terminated")
    }) // and shutdown when done
  }

}