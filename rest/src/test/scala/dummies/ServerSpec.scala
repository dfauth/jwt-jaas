package dummies

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{as, complete, entity, get, path, post}
import com.github.dfauth.jwt_jaas.jwt.Role.role
import com.github.dfauth.jwt_jaas.jwt.{JWTGenerator, JWTVerifier, KeyPairFactory, User}
import com.typesafe.scalalogging.LazyLogging
import dummies.MyDirectives._
import io.restassured.RestAssured._
import io.restassured.http.ContentType
import org.hamcrest.Matchers._
import org.scalatest.{FlatSpec, Matchers}
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration._

class ServerSpec extends FlatSpec with Matchers with LazyLogging with JsonSupport {

  val host = "localhost"
  val port = 9000


  "a hello endpoint" should "say hello" in {

    val route =
      path("hello") {
        get {
          complete(HttpEntity(ContentTypes.`application/json`, "{\"say\": \"hello\"}"))
        }
      }

    val endPoint = RestEndPointServer(route, host, port)
    val bindingFuture = endPoint.start()

    Await.result(bindingFuture, 5000.seconds)

    try {
      when().
        get(endPoint.endPointUrl("hello")).
        then().
        statusCode(200).
        body("say",equalTo("hello"));
      endPoint.stop(bindingFuture)
    } finally {
      endPoint.stop(bindingFuture)
    }
  }

  "a post endpoint" should "take a payload" in {

    val route =
      path("hello") {
        post {
          entity(as[Payload]) { p =>
            complete(HttpEntity(ContentTypes.`application/json`, s"""{"say": "hello to ${p.name}"}""""))
          }
        }
      }

    val endPoint = RestEndPointServer(route, host, port)
    val bindingFuture = endPoint.start()

    Await.result(bindingFuture, 5000.seconds)

    try {
      val name = "fred"
      given().contentType(ContentType.JSON).
        body(Payload(name).toJson.prettyPrint).log().body(true).
        post(endPoint.endPointUrl("hello")).
        then().log().body(true).
        statusCode(200).
        body("say",equalTo(s"hello to ${name}"));
      endPoint.stop(bindingFuture)
    } finally {
      endPoint.stop(bindingFuture)
    }
  }

  "a post endpoint" should "take a payload and parse it to a component" in {

    val component = Component("say hello to %s from a component")

    val route =
      path("hello") {
        post {
          entity(as[Payload]) { p =>
            val result = component.handle(p)
            complete(HttpEntity(ContentTypes.`application/json`, result.toJson.prettyPrint))
          }
        }
      }

    val endPoint = RestEndPointServer(route, host, port)
    val bindingFuture = endPoint.start()

    Await.result(bindingFuture, 5000.seconds)

    try {
      val name = "fred"
      given().contentType(ContentType.JSON).
        body(Payload(name).toJson.prettyPrint).log().body(true).
        post(endPoint.endPointUrl("hello")).
        then().log().body(true).
        statusCode(200).
        body("message",equalTo(s"say hello to ${name} from a component"));
      endPoint.stop(bindingFuture)
    } finally {
      endPoint.stop(bindingFuture)
    }
  }

    //User.of("fred", role("test:admin"), role("test:user"))

  //  def authenticate(jwtVerifier: JWTVerifier): Directive1[User] = for {
//    jwtToken <- optionalHeaderValueByName("Authorization").map(v => v.map(_.split(" ").toList.tail)
//    userData <- jwtVerifier.authenticateToken(jwtToken, u => {})
//  } yield userData

  "any endpoint" should "be able to be authenticated" in {

    val component = Component("say hello to %s from a component")

    val testKeyPair = KeyPairFactory.createKeyPair("RSA", 2048)
    val jwtVerifier = new JWTVerifier(testKeyPair.getPublic)


    val route =
      path("hello") {
        get {
          authenticate(jwtVerifier) { user =>
            complete(HttpEntity(ContentTypes.`application/json`, s"""{"say": "hello ${user}"}"""))
          }
        }
      }

    val endPoint = RestEndPointServer(route, host, port)
    val bindingFuture = endPoint.start()

    Await.result(bindingFuture, 5000.seconds)

    try {
      val jwtGenerator = new JWTGenerator(testKeyPair.getPrivate)
      val user = User.of("fred", role("test:admin"), role("test:user"))
      val token = jwtGenerator.generateToken(user)
      given().header("Authorization", "Bearer "+token).
        when().log().headers().
        get(endPoint.endPointUrl("hello")).
        then().
        statusCode(200).
        body("say",equalTo("hello"));
      endPoint.stop(bindingFuture)
    } finally {
      endPoint.stop(bindingFuture)
    }
  }

}

object auth {
  //  def authenticate[T](authenticator: Authenticator[T]): AuthenticationDirective[T] = {

//  private def authenticate: Directive1[Map[String, Any]] =
//    authenticateOrRejectWithChallenge(authenticator _)
//
//  def authenticator(credentials: Option[HttpCredentials]): Future[AuthenticationResult[String]] =
//    Future {
//      credentials match {
//        case Some(creds) if auth(creds) => Right("some-user-name-from-creds")
//        case _                          => Left(HttpChallenge("blah"))
//      }

//  def authenticate: Directive1[User] =
//    for {
//      credentials <- List(Some(GenericHttpCredentials))
//      result <- {
//        credentials match {
//          case Some(c) if c.scheme.equalsIgnoreCase("Bearer") => authenticate(c.token)
//          case _ => rejectUnauthenticated(AuthenticationFailedRejection.CredentialsMissing)
//        }
//      }
//    } yield result
//
//
//  def authenticate(token: String): Directive1[User] = {
//    validateAccessToken(token) match {
//      case Some(user) => provide(user)
//      case None => rejectUnauthenticated(AuthenticationFailedRejection.CredentialsRejected)
//    }
//  }
//
//  def validateAccessToken(str: String) = Some(User("fred"))
}

case class User1(name:String)
case class Payload(name:String)
case class Result(message:String)

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val payloadFormat:RootJsonFormat[Payload] = jsonFormat1(Payload)
  implicit val resultFormat:RootJsonFormat[Result] = jsonFormat1(Result)
}

case class Component(messageFormat:String) {
  def handle(payload: Payload): Result = {
    Result(String.format(messageFormat, payload.name))
  }
}