package dummies

import java.util.function.Consumer

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import com.github.dfauth.jwt_jaas.jwt.{JWTVerifier, User}

object MyDirectives {

  private def extractBearerToken(authHeader: Option[Authorization]): Option[String] =
    authHeader.collect {
      case Authorization(OAuth2BearerToken(token)) => token
    }

  //  def authenticate: Directive1[User] = ???
  def authenticate(verifier:JWTVerifier): Directive1[User] = {
    var user:User = null
    val d = optionalHeaderValueByName("Authorization")
//    val d = optionalHeaderValueByType(classOf[Authorization]).map(extractBearerToken)
      d.map(o => o.
        map(v =>
          v.split(" ").toList
          match {
      case x :: xs :: Nil => verifier.authenticateToken(xs, new Consumer[User](){
        override def accept(u: User): Unit = user = u
      })
      case _ => // reject
    }))
//    val user = User.of("fred", role("test:admin"), role("test:user"))
    provide(user)
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


