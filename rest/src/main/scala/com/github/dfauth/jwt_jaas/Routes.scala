package com.github.dfauth.jwt_jaas

import java.security.KeyPair
import java.time.{ZoneId, ZonedDateTime}

import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{as, complete, entity, get, path, post, reject}
import akka.http.scaladsl.server.Route
import com.github.dfauth.jwt_jaas.CredentialsJsonSupport._
import com.github.dfauth.jwt_jaas.MyDirectives.{authRejection, authenticate}
import com.github.dfauth.jwt_jaas.jwt._
import com.typesafe.scalalogging.LazyLogging
import spray.json.{JsonWriter, RootJsonFormat}

object Routes extends LazyLogging {

  val keyPair: KeyPair = KeyPairFactory.createKeyPair("RSA", 2048)
  val jwtBuilder = new JWTBuilder("me",keyPair.getPrivate)
  val jwtVerifier = new JWTVerifier(keyPair.getPublic)


  def hello(jwtVerifier: JWTVerifier) =
    path("hello") {
      get {
        authenticate(jwtVerifier) { user =>
          complete(HttpEntity(ContentTypes.`application/json`, s"""{"say": "hello to authenticated ${user.getUserId}"}"""))
        }
      }
    }

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

  def refresh =
    path("refresh") {
      get {
        authenticate(jwtVerifier) { u =>
          val authToken: String = jwtBuilder.forSubject(u.getUserId).withExpiry(u.getExpiry().atZone(ZoneId.systemDefault())).withClaim("roles", u.getRoles).build()
          complete(HttpEntity(ContentTypes.`application/json`, s"""{"authorizationToken": "${authToken}"}"""))
        }
      }
    }


  def genericGetEndpoint[T](f:User => T)(implicit w: JsonWriter[T]):Route =
    path("endpoint") {
      get {
        authenticate(jwtVerifier) { user =>
          val t:T = f(user)
          complete(HttpEntity(ContentTypes.`application/json`, w.write(t).prettyPrint))
        }
      }
    }

  def genericPostEndpoint[A, B](f:User => A => B)(implicit aReader: RootJsonFormat[A], bWriter: RootJsonFormat[B]):Route =
    path("endpoint") {
      post {
        authenticate(jwtVerifier) { user =>
          entity(as[A]) { a =>
            val b:B = f(user)(a)
            complete(HttpEntity(ContentTypes.`application/json`, bWriter.write(b).prettyPrint))
          }
        }
      }
    }
}