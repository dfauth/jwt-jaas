package com.github.dfauth.jwt_jaas.rest

import akka.http.scaladsl.server.Route
import com.github.dfauth.jwt_jaas.jwt.UserCtx
import com.github.dfauth.jwt_jaas.rest.JsonSupport._
import com.typesafe.scalalogging.LazyLogging
import io.restassured.http.ContentType
import io.restassured.response.Response
import org.hamcrest.Matchers._
import org.scalatest.{FlatSpec, Matchers}
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration._
import RestEndPointServer._

class DISpec extends FlatSpec with Matchers with LazyLogging {

  "any authenticated get endpoint" should "be able to propagate its user information" in {

    val component = TestComponent(user => Result[String](user.getUser.getUserId))

    import TestUtils._
    import akka.http.scaladsl.server.Directives._
    import com.github.dfauth.jwt_jaas.rest.Routes._

    val routes:Route = login(authenticateFred) ~ genericGet0Endpoint(component.handleWithUser)

    val endPoint = RestEndPointServer(routes, port = 0)
    val bindingFuture = endPoint.start()
    val binding = Await.result(bindingFuture, 5.seconds)
    implicit val loginEndpoint:String = RestEndPointServer.endPointUrl(binding, "login")

    try {
      val userId:String = "fred"
      val password:String = "password"
      val tokens:Tokens = asUser(userId).withPassword(password).login
      tokens.
        when().log().headers().
        get(endPointUrl(binding, "endpoint")).
        then().
        statusCode(200).
        body("result",equalTo(s"${userId}"))
    } finally {
      endPoint.stop(bindingFuture)
    }
  }

  "any authenticated post endpoint" should "be able to propagate its user information" in {

    val component = TestComponent2(user => (testPayload:Payload) => Result[String](s"${testPayload.payload} customised for ${user.getUser.getUserId}"))

    import TestUtils._
    import akka.http.scaladsl.server.Directives._
    import com.github.dfauth.jwt_jaas.rest.Routes._

    val routes:Route = login(authenticateFred) ~ genericPostEndpoint(component.handleWithUser)

    val endPoint = RestEndPointServer(routes, port = 0)
    val bindingFuture = endPoint.start()
    val binding = Await.result(bindingFuture, 5.seconds)
    implicit val loginEndpoint:String = endPointUrl(binding, "login")

    try {
      val userId:String = "fred"
      val password:String = "password"
      val tokens:Tokens = asUser(userId).withPassword(password).login

      val payload = "WOOZ"
      val bodyContent:String = Payload(payload).toJson.prettyPrint

      val response:Response = tokens.when.log().all().
        contentType(ContentType.JSON).
        body(bodyContent).
        post(endPointUrl(binding, "endpoint"))

      response.then().statusCode(200).
        body("result",equalTo(s"${payload} customised for ${userId}"))
    } finally {
      endPoint.stop(bindingFuture)
    }
  }

}

case class TestComponent[T](f:UserCtx=>T) {
  def handleWithUser(user: UserCtx):T = {
    f(user)
  }
}

case class TestComponent2[A,B](f:UserCtx=>A=>B) {
  def handleWithUser(user: UserCtx)(a:A):B = {
    f(user)(a)
  }
}









