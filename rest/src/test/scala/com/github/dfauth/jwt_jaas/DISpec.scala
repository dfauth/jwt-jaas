package com.github.dfauth.jwt_jaas

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Route
import com.github.dfauth.jwt_jaas.jwt.User
import com.typesafe.scalalogging.LazyLogging
import io.restassured.RestAssured._
import io.restassured.http.ContentType
import io.restassured.response.Response
import org.hamcrest.Matchers._
import org.scalatest.{FlatSpec, Matchers}
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration._

class DISpec extends FlatSpec with Matchers with LazyLogging with JsonSupport {

  "any authenticated get endpoint" should "be able to propagate its user information" in {

    val component = TestComponent(user => TestResult(user.getUserId))

    import DISpecJsonSupport._
    import TestUtils._
    import akka.http.scaladsl.server.Directives._
    import com.github.dfauth.jwt_jaas.Routes._

    val routes:Route = login(handle) ~ genericGetEndpoint(component.handleWithUser)

    val endPoint = RestEndPointServer(routes)
    implicit val loginEndpoint:String = endPoint.endPointUrl("login")
    val bindingFuture = endPoint.start()

    Await.result(bindingFuture, 5.seconds)

    try {
      val userId:String = "fred"
      val password:String = "password"
      val tokens:Tokens = asUser(userId).withPassword(password).login
      given().header("Authorization", "Bearer "+tokens.authorizationToken).
        when().log().headers().
        get(endPoint.endPointUrl("endpoint")).
        then().
        statusCode(200).
        body("payload",equalTo(s"${userId}"))
    } finally {
      endPoint.stop(bindingFuture)
    }
  }

  "any authenticated post endpoint" should "be able to propagate its user information" in {

    val component = TestComponent2(user => (testPayload:TestPayload) => TestResult(s"${testPayload.payload} customised for ${user.getUserId}"))

    import DISpecJsonSupport._
    import TestUtils._
    import akka.http.scaladsl.server.Directives._
    import com.github.dfauth.jwt_jaas.Routes._

    val routes:Route = login(handle) ~ genericPostEndpoint(component.handleWithUser)

    val endPoint = RestEndPointServer(routes)
    implicit val loginEndpoint:String = endPoint.endPointUrl("login")
    val bindingFuture = endPoint.start()

    Await.result(bindingFuture, 5.seconds)

    try {
      val userId:String = "fred"
      val password:String = "password"
      val tokens:Tokens = asUser(userId).withPassword(password).login

      import DISpecJsonSupport.testPayloadFormat

      val payload = "WOOZ"
      val bodyContent:String = TestPayload(payload).toJson.prettyPrint

      val response:Response = given().header("Authorization", "Bearer "+tokens.authorizationToken).
      when().log().all().contentType(ContentType.JSON).body(bodyContent).post(endPoint.endPointUrl("endpoint"))
      response.then().statusCode(200).
        body("payload",equalTo(s"${payload} customised for ${userId}"))
    } finally {
      endPoint.stop(bindingFuture)
    }
  }

}

case class TestComponent[T](f:User=>T) {
  def handleWithUser(user: User):T = {
    f(user)
  }
}

case class TestComponent2[A,B](f:User=>A=>B) {
  def handleWithUser(user: User)(a:A):B = {
    f(user)(a)
  }
}

case class TestPayload(payload:String)
case class TestResult(payload:String)


object DISpecJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val testPayloadFormat:RootJsonFormat[TestPayload] = jsonFormat1(TestPayload)
  implicit val testResultFormat:RootJsonFormat[TestResult] = jsonFormat1(TestResult)
}








