package com.github.dfauth.jwt_jaas.rest

import java.security.{KeyPair, PublicKey}
import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{as, complete, entity, path, post, reject}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.github.dfauth.jwt_jaas.jwt._
import com.github.dfauth.jwt_jaas.rest.MyDirectives.authRejection
import com.github.dfauth.jwt_jaas.rest.CredentialsJsonSupport._
import com.typesafe.scalalogging.LazyLogging
import spray.json._

import scala.concurrent.Future

class AuthenticationService[T](hostname:String = "localhost",
                            port:Int = 8080,
                            f:Credentials => Option[User],
                            brokerList:String = "localhost:9092",
                            topic:String = "session",
//                            deserializer:JsValue => T,
//                            serializer:T => JsValue,
                            keyPair:KeyPair = KeyPairFactory.createKeyPair("RSA", 2048)) extends LazyLogging {

  implicit val system = ActorSystem("authenticationService")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val jwtBuilder = new JWTBuilder("me", keyPair.getPrivate)

  def login(f:Credentials => Option[User]):Route =
    path("login") {
      post {
        entity(as[Credentials]) { c =>
          val user:Option[User] = f(c)
          user.map { u =>
            val authToken: String = jwtBuilder.forSubject(u.getUserId).withExpiry(u.getExpiry.toEpochMilli).withClaim("roles", u.getRoles).build()
            val refreshToken: String = jwtBuilder.forSubject(u.getUserId).withExpiry(ZonedDateTime.now().plusDays(1)).withClaim("roles", Set(Role.role("refresh"))).build()
            complete(HttpEntity(ContentTypes.`application/json`, s"""{"authorizationToken": "${authToken}", "refreshToken": "${refreshToken}"}"""))
          }.getOrElse(reject(authRejection))
        }
      }
    }

  def start():Future[ServerBinding] = {
    Http().bindAndHandle(login(f), hostname, port)
  }

  def stop(bindingFuture:Future[ServerBinding]):Unit = {
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => {
      system.terminate()
      logger.info("system terminated")
    }) // and shutdown when done
  }

  def getPublicKey:PublicKey = keyPair.getPublic

}
