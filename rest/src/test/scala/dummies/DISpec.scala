package dummies

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Route
import com.github.dfauth.jwt_jaas.jwt.{Role, User}
import com.typesafe.scalalogging.LazyLogging
import dummies.CredentialsJsonSupport.credentialsFormat
import io.restassured.RestAssured._
import io.restassured.http.ContentType
import io.restassured.response.Response
import org.hamcrest.Matchers._
import org.scalatest.{FlatSpec, Matchers}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.Await
import scala.concurrent.duration._

class DISpec extends FlatSpec with Matchers with LazyLogging with JsonSupport {

  "any authenticated get endpoint" should "be able to propagate its user information" in {

    val component = TestComponent(user => TestResult(user.getUserId))

    import DISpecJsonSupport._
    import Routes._
    import TestUtils._
    import akka.http.scaladsl.server.Directives._

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
    import Routes._
    import TestUtils._
    import akka.http.scaladsl.server.Directives._

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
      import spray.json._

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
  def handleWithUser(user: User): () => T = {
    () => f(user)
  }
}

case class TestComponent2[A,B](f:User=>A=>B) {
  def handleWithUser(user: User): A => B = {
    a => f(user)(a)
  }
}

case class TestPayload(payload:String)
case class TestResult(payload:String)


object DISpecJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val testPayloadFormat:RootJsonFormat[TestPayload] = jsonFormat1(TestPayload)
  implicit val testResultFormat:RootJsonFormat[TestResult] = jsonFormat1(TestResult)
}

object TestUtils {
  def handle(credentials:Credentials): Option[User]= {
    if(credentials.equals(Credentials("fred","password"))) {
      Some(User.of("fred", Role.role("admin"), Role.role("user")))
    } else {
      None
    }
  }
  def asUser(userId: String)(implicit endpoint:String):LoginBuilder = new LoginBuilder(endpoint,userId)
}

class LoginBuilder(endpoint:String, userId:String) {
  def withPassword(password:String): CredentialsBuilder = new CredentialsBuilder(endpoint, userId, password)
}

import dummies.CredentialsJsonSupport._

class CredentialsBuilder(endpoint:String, userId:String, password:String) {
  def login:Tokens = {

    val bodyContent:String = credentialsFormat.write(Credentials(userId, password)).toJson.prettyPrint
    val response:Response = given().when().log().all().contentType(ContentType.JSON).body(bodyContent).post(endpoint)

    response.then().statusCode(200)
    val authorizationToken = response.body.path[String]("authorizationToken")
    assert(authorizationToken != null)
    assert(authorizationToken.length > 0)
    val refreshToken = response.body.path[String]("refreshToken")
    assert(refreshToken != null)
    assert(refreshToken.length > 0)
    assert(!refreshToken.equals(authorizationToken))
    Tokens(authorizationToken, refreshToken)
  }
}

case class Tokens(authorizationToken:String, refreshToken:String)







