package com.github.dfauth.jwt_jaas.discovery

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow

object AkkaHttpProxy {

}

class AkkaHttpProxy {
  implicit val system = ActorSystem("Proxy")

  implicit val materializer = ActorMaterializer()

  implicit val ec = system.dispatcher

  val clientConnection:Flow[HttpRequest, HttpResponse, Any] = Http().outgoingConnection("localhost", 8081)
  Http().bindAndHandle(clientConnection, "localhost", 8080)

}