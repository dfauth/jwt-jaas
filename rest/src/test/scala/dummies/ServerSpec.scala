package dummies

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{as, complete, entity, get, path, post}
import com.github.dfauth.jwt_jaas.jwt.Role.role
import com.github.dfauth.jwt_jaas.jwt.{JWTGenerator, JWTVerifier, KeyPairFactory, User}
import com.typesafe.scalalogging.LazyLogging
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
    } finally {
      endPoint.stop(bindingFuture)
    }
  }

  "any endpoint" should "be able to be authenticated" in {

    val component = Component("say hello to %s from a component")

    val testKeyPair = KeyPairFactory.createKeyPair("RSA", 2048)
    val jwtVerifier = new JWTVerifier(testKeyPair.getPublic)


    import Routes._
    val endPoint = RestEndPointServer(hello(jwtVerifier), host, port)
    val bindingFuture = endPoint.start()

    Await.result(bindingFuture, 5.seconds)

    try {
      val userId: String = "fred"

      val jwtGenerator = new JWTGenerator(testKeyPair.getPrivate)
      val user = User.of(userId, role("test:admin"), role("test:user"))
      val token = jwtGenerator.generateToken(user.getUserId, "user", user)
      given().header("Authorization", "Bearer "+token).
        when().log().headers().
        get(endPoint.endPointUrl("hello")).
        then().
        statusCode(200).
        body("say",equalTo(s"hello to authenticated ${userId}"));
    } finally {
      endPoint.stop(bindingFuture)
    }
  }

  "any tampering with the token" should "fail to be authenticated" in {

    val component = Component("say hello to %s from a component")

    val testKeyPair = KeyPairFactory.createKeyPair("RSA", 2048)
    val jwtVerifier = new JWTVerifier(testKeyPair.getPublic)


    import Routes._
    val endPoint = RestEndPointServer(hello(jwtVerifier), host, port)
    val bindingFuture = endPoint.start()

    Await.result(bindingFuture, 5.seconds)

    try {
      val userId: String = "fred"

      val jwtGenerator = new JWTGenerator(testKeyPair.getPrivate)
      val user = User.of(userId, role("test:admin"), role("test:user"))
      val token = jwtGenerator.generateToken(user.getUserId, "user", user)
      val token1 = token.map(_ match {
        case 'a' => 'z'
        case 'z' => 'a'
        case c => c
      })
      given().header("Authorization", "Bearer "+token1).
        when().log().headers().
        get(endPoint.endPointUrl("hello")).
        then().
        statusCode(401)
    } finally {
      endPoint.stop(bindingFuture)
    }
  }

  "any user" should "be able to authenticate" in {

    val component = Component("say hello to %s from a component")

    import Routes._
    val endPoint = RestEndPointServer(login(component.handle), host, port)
    val bindingFuture = endPoint.start()

    Await.result(bindingFuture, 5.seconds)

    try {
      val userId: String = "fred"
      val password: String = "password"

      given().
        when().log().body().
        body(Credentials(userId, password)).
        post(endPoint.endPointUrl("login")).
        then().
        statusCode(200)
    } finally {
      endPoint.stop(bindingFuture)
    }
  }

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
  def handle(credentials:Credentials): Option[MyUser]= {
    if(credentials.equals(Credentials("fred","password"))) {
      Some(MyUser("fred", Array("admin", "user")))
    } else {
      None
    }
  }
}