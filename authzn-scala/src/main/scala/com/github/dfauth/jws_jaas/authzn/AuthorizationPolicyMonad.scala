package com.github.dfauth.jws_jaas.authzn

import java.util.concurrent.Callable
import scala.collection.JavaConverters._

import scala.util.{Failure, Success, Try}

case class AuthorizationPolicyMonad(directives:Directive*) {

  val policy:AuthorizationPolicy = new AuthorizationPolicyImpl(directives.asJava)

  def permit[T](subject: Subject, permission: Permission)(codeBlock : => T):Try[T] = {
    try {
      return Success(policy.permit(subject, permission).run[T](new Callable[T]() {
        override def call(): T = codeBlock
      }))
    } catch {
      case t:SecurityException => Failure[T](t)
      case t:RuntimeException => Failure[T](t)
    }
  }

}
