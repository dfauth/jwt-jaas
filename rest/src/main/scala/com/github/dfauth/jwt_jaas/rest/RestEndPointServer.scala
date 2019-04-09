package com.github.dfauth.jwt_jaas.rest

import java.net.MalformedURLException

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.RouteConcatenation._
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object RestEndPointServer {

  implicit val system = ActorSystem("my-system")
  implicit val materializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher

  def endPointUrl(binding:ServerBinding, file: String, protocol:String = "http"):String = {
    s"${protocol}://${binding.localAddress.getHostName}:${binding.localAddress.getPort}/${file}"
  }

  def endPointUri(binding:ServerBinding, file: String, protocol:String = "http"):Try[Uri] = {
    try {
      val scheme = s"${protocol}://${binding.localAddress.getHostName}:${binding.localAddress.getPort}/"
      Success(Uri.from(scheme = protocol, host = binding.localAddress.getHostName, port = binding.localAddress.getPort, path = "/"+file))
    } catch {
      case e:MalformedURLException => Failure(e)
    }
  }
}

case class RestEndPointServer(route:Route, hostname:String = "localhost", port:Int = 8080) extends LazyLogging {

  implicit val system = ActorSystem("my-system")
  implicit val materializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher

  def start(route:Route):Future[ServerBinding] = start(Option(route))

  def start(additionalRoute:Option[Route] = None):Future[ServerBinding] = {
    val r = additionalRoute.map(r => r ~ route).getOrElse(route)
    Http().bindAndHandle(r, hostname, port)
  }

  def stop(bindingFuture:Future[ServerBinding]):Unit = {
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => {
      system.terminate()
      logger.info("system terminated")
    }) // and shutdown when done
  }

}