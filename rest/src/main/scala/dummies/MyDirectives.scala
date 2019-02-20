package dummies

import akka.http.scaladsl.model.headers.HttpChallenge
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{AuthenticationFailedRejection, Directive1, Rejection}
import com.github.dfauth.jwt_jaas.jwt.JWTVerifier.TokenAuthentication.{Failure, Success}
import com.github.dfauth.jwt_jaas.jwt.{JWTVerifier, User}
import com.typesafe.scalalogging.LazyLogging

object MyDirectives extends LazyLogging {

  private def extractBearerToken(authHeader: Option[String]): Option[String] =
    authHeader.filter(_.startsWith("Bearer ")).map(token => token.substring("Bearer ".length))

  private def bearerToken: Directive1[Option[String]] =
    for {
      authBearerHeader <- optionalHeaderValueByName("Authorization").map(extractBearerToken)
//      xAuthCookie <- optionalCookie("X-Authorization-Token").map(_.map(_.value))
    } yield authBearerHeader //.orElse(xAuthCookie)

  def authRejection: Rejection = AuthenticationFailedRejection(AuthenticationFailedRejection.CredentialsRejected, HttpChallenge("", ""))

  def authenticate(verifier:JWTVerifier): Directive1[User] = {
    var user:User = null
    bearerToken.flatMap {
      case Some(token) => {
        verifier.authenticateToken(token, verifier.asUser) match {
          case s:Success[User] => provide(s.getPayload)
          case f:Failure[User] => reject(authRejection)
        }
      }
      case None => reject(authRejection)
    }
  }
}


