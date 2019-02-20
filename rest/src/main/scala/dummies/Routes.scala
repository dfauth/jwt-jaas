package dummies

import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{as, complete, entity, get, path, post, reject}
import com.github.dfauth.jwt_jaas.jwt.JWTVerifier
import dummies.CredentialsJsonSupport._
import dummies.MyDirectives.authenticate
import dummies.MyDirectives.authRejection

object Routes {

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
          val user:Option[MyUser] = f(c)
          user.map { u =>
            complete(HttpEntity(ContentTypes.`application/json`, s"""{"say": "hello to authenticated ${u.userId}"}"""))
          }.getOrElse(reject(authRejection))
        }
      }
    }
}
