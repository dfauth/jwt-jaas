package com.github.dfauth.jwt_jaas.authzn

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

case class AuthorizationPolicyMonad(directives:Directive*) {

  val policy:AuthorizationPolicy = new AuthorizationPolicyImpl(directives.asJava)

  def permit[T](subject: Subject, permission: Permission)(codeBlock : => T):Try[T] = {
    try {
      return Success(policy.permit(subject, permission).run[T](() => codeBlock))
    } catch {
      case t:SecurityException => Failure[T](t)
      case t:RuntimeException => Failure[T](t)
    }
  }

}
