package com.github.dfauth.jwt_jaas.rest

import akka.http.scaladsl.model.headers.HttpChallenge
import akka.http.scaladsl.server.{AuthenticationFailedRejection, Directive1, Rejection}
import akka.http.scaladsl.server.Directives.{optionalHeaderValueByName, provide, reject}
import com.github.dfauth.jwt_jaas.jwt.JWTVerifier.TokenAuthentication.{Failure, Success}
import com.github.dfauth.jwt_jaas.jwt.{JWTVerifier, User, UserCtx}

trait TokenValidator {

  val jwtVerifier:JWTVerifier

  def authenticate: Directive1[UserCtx] = {
    var user:User = null
    bearerToken.flatMap {
      case Some(token) => {
        jwtVerifier.authenticateToken(token, jwtVerifier.asUser) match {
          case s:Success[User] => provide(new UserCtx(token, s.getPayload))
          case f:Failure[User] => reject(authRejection)
        }
      }
      case None => reject(authRejection)
    }
  }

  private def extractBearerToken(authHeader: Option[String]): Option[String] =
    authHeader.filter(_.startsWith("Bearer ")).map(token => token.substring("Bearer ".length))

  private def bearerToken: Directive1[Option[String]] =
    for {
      authBearerHeader <- optionalHeaderValueByName("Authorization").map(extractBearerToken)
    } yield authBearerHeader

  def authRejection: Rejection = AuthenticationFailedRejection(AuthenticationFailedRejection.CredentialsRejected, HttpChallenge("", ""))

}
