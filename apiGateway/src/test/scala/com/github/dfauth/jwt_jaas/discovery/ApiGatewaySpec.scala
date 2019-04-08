package com.github.dfauth.jwt_jaas.discovery

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{complete, get, path, _}
import com.github.dfauth.jwt_jaas.rest.RestEndPointServer
import com.github.dfauth.jwt_jaas.rest.RestEndPointServer._
import com.typesafe.scalalogging.LazyLogging
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.hamcrest.Matchers.equalTo
import org.scalatest.{FlatSpec, Matchers}
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class ApiGatewaySpec
  extends FlatSpec
  with Matchers
  with LazyLogging {

  import JsonSupport._

  def microservice(p: String): Future[Http.ServerBinding] = {
    val route =
      get {
        path(p) {
          logger.info(s"get call to ${p}")
          complete(HttpEntity(ContentTypes.`application/json`, s"""{"path": "${p}"}"""))
        }
      } ~ post {
        path(p) {
          entity(as[JsValue]) { v =>
            logger.info(s"get call to ${p}")
            complete(HttpEntity(ContentTypes.`application/json`, s"""{"body": ${v}}"""))
          }
        }
      }

    RestEndPointServer(route, port = 0).start()
  }

  "an api gateway" should "act as a reverse proxy" in {

    // start the discovery service
    val apiGateway = new ApiGateway(port = 0)
    val fBinding = apiGateway.start()
    // wait for startup
    val binding:Http.ServerBinding = Await.result(fBinding, 1.seconds)
    // get its local binder
    val binder = ApiGateway.binder(binding)

    // start a simple microservice on a random port
    // and wait for startup
    val binding1 = Await.result(microservice("hello"), 1.seconds)

    // bind it to the local discovery service
    apiGateway.bind(Binding("hello", endPointUrl(binding1, "hello")))

    // start a simple microservice on a random port
    // and wait for startup
    val binding2 = Await.result(microservice("goodbye"), 1.seconds)

    // remote bind to the discovery service
    binder.bind("goodbye", binding2)

    try {
      given().log().all().contentType(ContentType.JSON).
        get(endPointUrl(binding, "hello")).
        then().log().body(true).
        statusCode(200).
        body("path",equalTo("hello"));
      given().log().all().contentType(ContentType.JSON).
        get(endPointUrl(binding, "goodbye")).
        then().log().body(true).
        statusCode(200).
        body("path",equalTo("goodbye"));
      given().log().all().contentType(ContentType.JSON).
        body("""{"a":1, "b":2}""").
        post(endPointUrl(binding, "hello")).
        then().log().body(true).
        statusCode(200).
        body("body.b",equalTo(2));
      given().log().all().contentType(ContentType.JSON).
        body("""{"c":3, "d":4}""").
        post(endPointUrl(binding, "goodbye")).
        then().log().body(true).
        statusCode(200).
        body( "body.d",equalTo(4));
    } finally {
      apiGateway.stop(fBinding)
    }

  }
}
