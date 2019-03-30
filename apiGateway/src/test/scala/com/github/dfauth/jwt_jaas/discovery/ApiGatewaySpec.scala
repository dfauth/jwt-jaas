package com.github.dfauth.jwt_jaas.discovery

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{complete, get, path}
import com.github.dfauth.jwt_jaas.rest.RestEndPointServer
import com.typesafe.scalalogging.LazyLogging
import io.restassured.RestAssured.given
import org.hamcrest.Matchers.equalTo
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

import RestEndPointServer._

class ApiGatewaySpec
  extends FlatSpec
  with Matchers
  with LazyLogging {

  def microservice(path1: String): Future[Http.ServerBinding] = {
    val route =
      path(path1) {
        get {
          logger.info(s"call to ${path1}")
          complete(HttpEntity(ContentTypes.`application/json`, s"""{"path": "${path1}"}""")) // {"path": "${path1}"}
        }
      }

    val dummyRoute =
      path("dummy") {
        get {
          logger.info(s"call to dummy")
          complete(HttpEntity(ContentTypes.`application/json`, s"""{"path": "dummy"}""")) // {"path": "${path1}"}
        }
      }

    RestEndPointServer(route, port = 0).start()
  }

  "an api gateway" should "act as a reverse proxy" in {

    // start the discovery service
    val discovery = new ApiGateway()
    val fBinding = discovery.start()
    // wait for startup
    val binding:Http.ServerBinding = Await.result(fBinding, 1.seconds)
    // get its local binder
    val binder = ApiGateway.binder(binding)

    // start a simple microservice on a random port
    // and wait for startup
    val binding1 = Await.result(microservice("hello"), 1.seconds)

    // bind it to the discovery service
    binder.bind("hello", binding1)

    // start a simple microservice on a random port
    // and wait for startup
    val binding2 = Await.result(microservice("goodbye"), 1.seconds)

    // bind it to the discovery service
    binder.bind("goodbye", binding2)

    try {
      given().log().all().get(endPointUrl(binding, "hello")).
        then().log().body(true).
        statusCode(200).
        body("route",equalTo("hello"));
    } finally {
      discovery.stop(fBinding)
    }

  }
}
