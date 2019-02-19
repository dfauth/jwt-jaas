package dummies

import akka.http.scaladsl.model.headers.HttpChallenge
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{AuthenticationFailedRejection, Directive1, Rejection}
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

  private def authRejection: Rejection = AuthenticationFailedRejection(AuthenticationFailedRejection.CredentialsRejected, HttpChallenge("", ""))

  def authenticate(verifier:JWTVerifier): Directive1[User] = {
    var user:User = null
    bearerToken.flatMap {
      case Some(token) => {
        val user:User = verifier.authenticateToken(token, verifier.asUser)
        provide(user)
      }
      case None => reject(authRejection)
    }
//    token = optionalHeaderValueByName("Authorization").map(extractBearerToken)
//    verifier.authenticateToken(token, new Consumer[User] {
//      override def accept(t: User): Unit = user = t
//    })
//    val user = User.of("fred", role("test:admin"), role("test:user"))
  }
//    Directive.apply[Tuple1[User]](f => complete(Tuple1(user)))
//  }
//    onComplete(f) flatMap {
//      case Success(t) =>
//        provide(t)
//      case Failure(error) =>
//        complete(apiError.statusCode, apiError.message)
//    }


}


