package com.github.dfauth.jwt_jaas.rest

import akka.actor.ActorSystem
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future


trait ServiceLifecycle extends LazyLogging{

  val name:String
  implicit val system = ActorSystem(name)
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  def start():Future[ServerBinding]

  def stop(bindingFuture:Future[ServerBinding]):Unit = {
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => {
      system.terminate()
      logger.info("system terminated")
    }) // and shutdown when done
  }

}