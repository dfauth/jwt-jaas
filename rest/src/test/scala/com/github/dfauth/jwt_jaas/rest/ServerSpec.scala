package com.github.dfauth.jwt_jaas.rest

import java.time.ZonedDateTime

import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{as, complete, entity, get, path, post}
import com.github.dfauth.jwt_jaas.jwt.Role.role
import com.github.dfauth.jwt_jaas.jwt._
import com.github.dfauth.jwt_jaas.rest.JsonSupport._
import com.typesafe.scalalogging.LazyLogging
import io.restassured.RestAssured._
import io.restassured.http.ContentType
import org.hamcrest.Matchers._
import org.scalatest.{FlatSpec, Matchers}
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration._
import RestEndPointServer._

class ServerSpec extends FlatSpec with Matchers with LazyLogging {

  val host = "localhost"
  val port = 0


  "a hello endpoint" should "say hello" in {

    val route =
      path("hello") {
        get {
          complete(HttpEntity(ContentTypes.`application/json`, "{\"say\": \"hello\"}"))
        }
      }

    val endPoint = RestEndPointServer(route, host, port)
    val bindingFuture = endPoint.start()

    val binding = Await.result(bindingFuture, 5000.seconds)

    try {
      when().
        get(endPointUrl(binding, "hello")).
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
            complete(HttpEntity(ContentTypes.`application/json`, s"""{"say": "hello to ${p.payload}"}""""))
          }
        }
      }

    val endPoint = RestEndPointServer(route, host, port)
    val bindingFuture = endPoint.start()

    val binding = Await.result(bindingFuture, 5000.seconds)

    try {
      val name = "fred"
      given().contentType(ContentType.JSON).
        body(Payload(name).toJson.prettyPrint).log().body(true).
        post(endPointUrl(binding, "hello")).
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

    val binding = Await.result(bindingFuture, 5000.seconds)

    try {
      val name = "fred"
      given().contentType(ContentType.JSON).
        body(Payload(name).toJson.prettyPrint).log().body(true).
        post(endPointUrl(binding, "hello")).
        then().log().body(true).
        statusCode(200).
        body("result",equalTo(s"say hello to ${name} from a component"));
    } finally {
      endPoint.stop(bindingFuture)
    }
  }

  "any endpoint" should "be able to be authenticated" in {

    val component = Component("say hello to %s from a component")

    val testKeyPair = KeyPairFactory.createKeyPair("RSA", 2048)
    val jwtVerifier = new JWTVerifier(testKeyPair.getPublic)


    import com.github.dfauth.jwt_jaas.rest.Routes._
    val endPoint = RestEndPointServer(hello(jwtVerifier), host, port)
    val bindingFuture = endPoint.start()

    val binding = Await.result(bindingFuture, 5.seconds)

    try {
      val userId: String = "fred"

      val jwtBuilder = new JWTBuilder("me",testKeyPair.getPrivate)
      val user = User.of(userId, role("test:admin"), role("test:user"))
      val token = jwtBuilder.forSubject(user.getUserId).withClaim("roles", user.getRoles).withExpiry(ZonedDateTime.now().plusMinutes(20)).build()
      given().header("Authorization", "Bearer "+token).
        when().log().headers().
        get(endPointUrl(binding, "hello")).
        then().
        statusCode(200).
        body("say",equalTo(s"hello to authenticated ${userId}"))
    } finally {
      endPoint.stop(bindingFuture)
    }
  }

  "any tampering with the token" should "fail to be authenticated" in {

    val component = Component("say hello to %s from a component")

    val testKeyPair = KeyPairFactory.createKeyPair("RSA", 2048)
    val jwtVerifier = new JWTVerifier(testKeyPair.getPublic)


    import com.github.dfauth.jwt_jaas.rest.Routes._
    val endPoint = RestEndPointServer(hello(jwtVerifier), host, port)
    val bindingFuture = endPoint.start()

    val binding = Await.result(bindingFuture, 5.seconds)

    try {
      val userId: String = "fred"

      val jwtBuilder = new JWTBuilder("me",testKeyPair.getPrivate)
      val user = User.of(userId, role("test:admin"), role("test:user"))
      val token = jwtBuilder.forSubject(user.getUserId).withClaim("roles", user.getRoles).build()
      val token1 = token.map(_ match {
        case 'a' => 'z'
        case 'z' => 'a'
        case c => c
      })
      given().header("Authorization", "Bearer "+token1).
        when().log().headers().
        get(endPointUrl(binding, "hello")).
        then().
        statusCode(401)
    } finally {
      endPoint.stop(bindingFuture)
    }
  }

  "any user" should "be able to authenticate" in {

    import com.github.dfauth.jwt_jaas.rest.CredentialsJsonSupport._

    val component = Component("say hello to %s from a component")

    import com.github.dfauth.jwt_jaas.rest.Routes._
    val endPoint = RestEndPointServer(login(component.handle), host, port)
    val bindingFuture = endPoint.start()

    val binding = Await.result(bindingFuture, 5.seconds)

    try {
      val userId: String = "fred"
      val password: String = "password"

      val response = given().
        when().log().body().contentType(ContentType.JSON).
        body(Credentials(userId, password).toJson.prettyPrint).
        post(endPointUrl(binding, "login"))
        response.then().statusCode(200)
      val authorizationToken = response.body.path[String]("authorizationToken")
      logger.info(s"authorizationToken: ${authorizationToken}")
      authorizationToken should not be null
      val refreshToken = response.body.path[String]("refreshToken")
      logger.info(s"refreshToken: ${refreshToken}")
      refreshToken should not be null
      refreshToken should not be authorizationToken
    } finally {
      endPoint.stop(bindingFuture)
    }
  }

  "any user providing the wrong password" should "not be authenticated" in {

    import com.github.dfauth.jwt_jaas.rest.CredentialsJsonSupport._

    val component = Component("say hello to %s from a component")

    import com.github.dfauth.jwt_jaas.rest.Routes._
    val endPoint = RestEndPointServer(login(component.handle), host, port)
    val bindingFuture = endPoint.start()

    val binding = Await.result(bindingFuture, 5.seconds)

    try {
      val userId: String = "fred"
      val password: String = "blah"

      given().
        when().log().body().contentType(ContentType.JSON).
        body(Credentials(userId, password).toJson.prettyPrint).
        post(endPointUrl(binding, "login")).
        then().
        statusCode(401)
    } finally {
      endPoint.stop(bindingFuture)
    }
  }

  "any user providing the wrong username" should "not be authenticated" in {

    import com.github.dfauth.jwt_jaas.rest.CredentialsJsonSupport._

    val component = Component("say hello to %s from a component")

    import com.github.dfauth.jwt_jaas.rest.Routes._
    val endPoint = RestEndPointServer(login(component.handle), host, port)
    val bindingFuture = endPoint.start()

    val binding = Await.result(bindingFuture, 5.seconds)

    try {
      val userId: String = "wilma"
      val password: String = "password"

      given().
        when().log().body().contentType(ContentType.JSON).
        body(Credentials(userId, password).toJson.prettyPrint).
        post(endPointUrl(binding, "login")).
        then().
        statusCode(401)
    } finally {
      endPoint.stop(bindingFuture)
    }
  }

  "any user providing both the wrong username and wrong password" should "not be authenticated" in {

    import com.github.dfauth.jwt_jaas.rest.CredentialsJsonSupport._

    val component = Component("say hello to %s from a component")

    import com.github.dfauth.jwt_jaas.rest.Routes._
    val endPoint = RestEndPointServer(login(component.handle), host, port)
    val bindingFuture = endPoint.start()

    val binding = Await.result(bindingFuture, 5.seconds)

    try {
      val userId: String = "wilma"
      val password: String = "blah"

      given().
        when().log().body().contentType(ContentType.JSON).
        body(Credentials(userId, password).toJson.prettyPrint).
        post(endPointUrl(binding, "login")).
        then().
        statusCode(401)
    } finally {
      endPoint.stop(bindingFuture)
    }
  }

}


private case class Component(messageFormat:String) {
  def handle(payload: Payload): Result[String] = {
    Result(String.format(messageFormat, payload.payload))
  }
  def handle(credentials:Credentials): Option[User]= {
    if(credentials.equals(Credentials("fred","password"))) {
      Some(User.of("fred", Role.role("admin"), Role.role("user")))
    } else {
      None
    }
  }
}