package dummies

import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{complete, get, path}
import com.github.dfauth.jwt_jaas.jwt.JWTVerifier
import dummies.MyDirectives.authenticate

object Routes {

  def hello(jwtVerifier: JWTVerifier) =
    path("hello") {
      get {
        authenticate(jwtVerifier) { user =>
          complete(HttpEntity(ContentTypes.`application/json`, s"""{"say": "hello to authenticated ${user.getUserId}"}"""))
        }
      }
    }
}
