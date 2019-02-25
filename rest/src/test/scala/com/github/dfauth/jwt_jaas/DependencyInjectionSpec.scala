package com.github.dfauth.jwt_jaas

import akka.http.scaladsl.server.Route
import com.github.dfauth.jwt_jaas.jwt.User
import com.typesafe.scalalogging.LazyLogging
import io.restassured.http.ContentType
import io.restassured.response.Response
import org.hamcrest.Matchers._
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

class DependencyInjectionSpec extends FlatSpec with Matchers with LazyLogging {

  "any authenticated get endpoint" should "be able to propagate its user information though a pipeline of chained functions" in {

    import TestUtils._
    import akka.http.scaladsl.server.Directives._
    import com.github.dfauth.jwt_jaas.JsonSupport._
    import com.github.dfauth.jwt_jaas.Routes._
    import spray.json._

    val compose:User => String => Result[Int] = {
      user => (s:String) => Result(s.toInt)
    }

    val routes:Route = login(handle) ~ genericGet1Endpoint(compose)(intResultFormat)

    val endPoint = RestEndPointServer(routes)
    implicit val loginEndpoint:String = endPoint.endPointUrl("login")
    val bindingFuture = endPoint.start()

    Await.result(bindingFuture, 5.seconds)

    try {
      val userId:String = "fred"
      val password:String = "password"
      val tokens:Tokens = asUser(userId).withPassword(password).login

      val payload = "2"
      val bodyContent:String = Payload(payload).toJson.prettyPrint

      val response:Response = tokens.when.log().all().
        contentType(ContentType.JSON).
        get(endPoint.endPointUrl("endpoint/{payload}"), payload)

      response.then().log.all.statusCode(200).
        body("result",equalTo(payload.toInt))
    } finally {
      endPoint.stop(bindingFuture)
    }
  }

  "any authenticated post endpoint" should "be able to propagate its user information though a pipeline of chained functions" in {

    import TestUtils._
    import akka.http.scaladsl.server.Directives._
    import com.github.dfauth.jwt_jaas.JsonSupport._
    import com.github.dfauth.jwt_jaas.Routes._
    import spray.json._

    val compose:User => Payload => Result[Int] = {
      user => (p:Payload) => Result(p.payload.toInt)
    }

    val routes:Route = login(handle) ~ genericPostEndpoint(compose)

    val endPoint = RestEndPointServer(routes)
    implicit val loginEndpoint:String = endPoint.endPointUrl("login")
    val bindingFuture = endPoint.start()

    Await.result(bindingFuture, 5.seconds)

    try {
      val userId:String = "fred"
      val password:String = "password"
      val tokens:Tokens = asUser(userId).withPassword(password).login

      val payload = "2"
      val bodyContent:String = Payload(payload).toJson.prettyPrint

      val response:Response = tokens.when.log().all().
        contentType(ContentType.JSON).
        body(bodyContent).
        post(endPoint.endPointUrl("endpoint"))

      response.then().statusCode(200).
        body("result",equalTo(payload.toInt))
    } finally {
      endPoint.stop(bindingFuture)
    }
  }

}




















