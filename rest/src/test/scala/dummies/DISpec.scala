package dummies

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Route
import com.github.dfauth.jwt_jaas.jwt.{JWTVerifier, KeyPairFactory, Role, User}
import com.typesafe.scalalogging.LazyLogging
import io.restassured.RestAssured._
import io.restassured.http.ContentType
import io.restassured.response.Response
import org.hamcrest.Matchers._
import org.scalatest.{FlatSpec, Matchers}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.Await
import scala.concurrent.duration._

class DISpec extends FlatSpec with Matchers with LazyLogging with JsonSupport {

//  val host = "localhost"
//  val port = 9000


  "any authenticated endpoint" should "be able to propagate its user information" in {

    val component = TestComponent(user => TestResult(user.getUserId))

    val testKeyPair = KeyPairFactory.createKeyPair("RSA", 2048)
    val jwtVerifier = new JWTVerifier(testKeyPair.getPublic)


    import DISpecJsonSupport._
    import Routes._
    import TestUtils._
    import akka.http.scaladsl.server.Directives._

    val routes:Route = login(handle) ~ genericEndpoint(component.handleWithUser)

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
        body("userId",equalTo(s"${userId}"))
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

case class TestResult(userId:String)


object DISpecJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val testResultFormat:RootJsonFormat[TestResult] = jsonFormat1(TestResult)
}

/**

val response = given().
        when().log().body().contentType(ContentType.JSON).
        body(Credentials(userId, password).toJson.prettyPrint).
        post(endPoint.endPointUrl("login"))
        response.then().statusCode(200)
      val authorizationToken = response.body.path[String]("authorizationToken")
      logger.info(s"authorizationToken: ${authorizationToken}")
      authorizationToken should not be null
      val refreshToken = response.body.path[String]("refreshToken")
      logger.info(s"refreshToken: ${refreshToken}")
      refreshToken should not be null
      refreshToken should not be authorizationToken


  */
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







