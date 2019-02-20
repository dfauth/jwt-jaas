package dummies

import java.security.KeyPair
import java.util.Date

import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{as, complete, entity, get, path, post, reject}
import com.github.dfauth.jwt_jaas.jwt.{JWTGenerator, JWTVerifier, KeyPairFactory}
import com.typesafe.scalalogging.LazyLogging
import dummies.CredentialsJsonSupport._
import dummies.MyDirectives.{authRejection, authenticate}

object Routes extends LazyLogging {

  val keyPair: KeyPair = KeyPairFactory.createKeyPair("RSA", 2048)
  val jwtGenerator = new JWTGenerator(keyPair.getPrivate)


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
            val authToken: String = jwtGenerator.generateToken(u.userId, new Date(u.expiry), "user", u)
            val refreshToken: String = jwtGenerator.generateToken(u.userId, new Date(u.refresh), "user", u)
            complete(HttpEntity(ContentTypes.`application/json`, s"""{"authorizationToken": "${authToken}", "refreshToken": "${refreshToken}"}"""))
          }.getOrElse(reject(authRejection))
        }
      }
    }
}
