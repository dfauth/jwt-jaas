package com.github.dfauth.jwt_jaas.discovery

import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{complete, get, path}
import com.github.dfauth.jwt_jaas.rest.RestEndPointServer
import com.typesafe.scalalogging.LazyLogging
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.hamcrest.Matchers.equalTo
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

class AkkaHttpProxySpec
  extends FlatSpec
  with Matchers
  with LazyLogging {

  "a proxy service" should "act as a reverse proxy" in {

    // start the discovery service
    val discovery = new AkkaHttpProxy()

    // start a simple microservice on a random port
    val route =
      path("hello") {
        get {
          complete(HttpEntity(ContentTypes.`application/json`, "{\"say\": \"hello\"}"))
        }
      }

    val endPoint = RestEndPointServer(route, port = 8081)
    val bindingFuture = endPoint.start()

    // wait for startup
    val binding1 = Await.result(bindingFuture, 1.seconds)

    // bind it to the discovery service
//    DiscoveryService.bind("hello", binding)

    try {
      import JsonSupport._
      import spray.json._
      import RestEndPointServer._

      val json:String = Binding("hello", endPointUrl(binding1, "hello")).toJson.prettyPrint
      given().contentType(ContentType.JSON).
        get("http://localhost:8080/hello").
        then().log().body(true).
        statusCode(200).
        body("say",equalTo("hello"));
    } finally {
      endPoint.stop(bindingFuture)
    }

  }
}
