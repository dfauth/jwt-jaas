package dummies

import java.security.KeyPair
import java.time.ZoneId

import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{as, complete, entity, get, path, post, reject}
import com.github.dfauth.jwt_jaas.jwt.{JWTBuilder, JWTVerifier, KeyPairFactory}
import com.typesafe.scalalogging.LazyLogging
import dummies.CredentialsJsonSupport._
import dummies.MyDirectives.{authRejection, authenticate}

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

  def login(f:Credentials => Option[MyUser]) =
    path("login") {
      post {
        entity(as[Credentials]) { c =>
          logger.info(s"c: ${c}")
          val user:Option[MyUser] = f(c)
          user.map { u =>
            val authToken: String = jwtBuilder.forSubject(u.userId).withExpiry(u.expiry).withClaim("roles", u.roles).build()
            val refreshToken: String = jwtBuilder.forSubject(u.userId).withExpiry(u.refresh).withClaim("roles", Set("refresh")).build()
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
}
