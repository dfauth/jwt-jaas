package com.github.dfauth.jwt_jaas.rest

import java.security.KeyPair
import java.time.{ZoneId, ZonedDateTime}

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{as, complete, entity, get, onComplete, path, post, reject}
import akka.http.scaladsl.server.PathMatcher._
import akka.http.scaladsl.server.PathMatchers.Remaining
import akka.http.scaladsl.server.Route
import com.github.dfauth.jwt_jaas.jwt._
import com.github.dfauth.jwt_jaas.rest.CredentialsJsonSupport._
import com.github.dfauth.jwt_jaas.rest.MyDirectives.{authRejection, authenticate}
import com.typesafe.scalalogging.LazyLogging
import spray.json.{JsonWriter, RootJsonFormat}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object Routes extends LazyLogging {

  val keyPair: KeyPair = KeyPairFactory.createKeyPair("RSA", 2048)
  val jwtBuilder = new JWTBuilder("me",keyPair.getPrivate)
  val jwtVerifier = new JWTVerifier(keyPair.getPublic)


  def hello(jwtVerifier: JWTVerifier) =
    path("hello") {
      get {
        authenticate(jwtVerifier) { user =>
          complete(HttpEntity(ContentTypes.`application/json`, s"""{"say": "hello to authenticated ${user.getUser.getUserId}"}"""))
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
          val authToken: String = jwtBuilder.forSubject(u.getUser.getUserId).withExpiry(u.getUser.getExpiry().atZone(ZoneId.systemDefault())).withClaim("roles", u.getUser.getRoles).build()
          complete(HttpEntity(ContentTypes.`application/json`, s"""{"authorizationToken": "${authToken}"}"""))
        }
      }
    }


  def genericGet0Endpoint[T](f:UserCtx => T)(implicit w: JsonWriter[T]):Route =
    path("endpoint") {
      get {
        authenticate(jwtVerifier) { user =>
          val t:T = f(user)
          complete(HttpEntity(ContentTypes.`application/json`, w.write(t).prettyPrint))
        }
      }
    }

  def genericGet1Endpoint[T](f:UserCtx => String => T)(bWriter: RootJsonFormat[T]):Route =
    path("endpoint" / Remaining) { r =>
      get {
        authenticate(jwtVerifier) { user =>
          val t:T = f(user)(r)
          complete(HttpEntity(ContentTypes.`application/json`, bWriter.write(t).prettyPrint))
        }
      }
    }

  def genericPostEndpoint[A, B](f:UserCtx => A => B)(implicit aReader: RootJsonFormat[A], bWriter: RootJsonFormat[B]):Route =
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

  def genericPostFutureEndpoint[A, B](f:UserCtx => A => Future[B])(implicit aReader: RootJsonFormat[A], bWriter: RootJsonFormat[B]):Route =
    path("endpoint") {
      post {
        authenticate(jwtVerifier) { user =>
          entity(as[A]) { a =>
            val b:Future[B] = f(user)(a)
            onComplete(b) {
              case Success(s) => complete(HttpEntity(ContentTypes.`application/json`, bWriter.write(s).prettyPrint))
              case Failure(e) => complete((InternalServerError, s"${e.getMessage}"))
            }
          }
        }
      }
    }
}
