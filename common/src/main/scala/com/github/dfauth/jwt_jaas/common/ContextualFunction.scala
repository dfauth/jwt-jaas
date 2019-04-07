package com.github.dfauth.jwt_jaas.common

import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global


object ContextualFunction extends LazyLogging {

  def tryAdapter[A,B](f:UserCtx => A => B):UserCtx => Try[A] => Try[B] = {
    u => a => a match {
      case s:Success[A] => {
        logger.debug(s"tryAdapter Success: ${s.value}")
        Success[B](f(u)(s.value))
      }
      case f:Failure[A] => {
        logger.error(f.exception.getMessage, f.exception)
        Failure[B](f.exception)
      }
    }
  }

  def futureAdapter[A,B](f:UserCtx => A => B):UserCtx => Future[A] => Future[B] = {
    val z = (u:UserCtx) => (futureA:Future[A]) => {
      val p = Promise[B]
      futureA.onComplete {
        case s:Success[A] => p.success(f(u)(s.value))
        case f:Failure[A] => p.failure(f.exception)
      }
      p.future
    }
    z
  }
}

abstract class ContextualFunction[A,B](f:UserCtx => A => B) extends Function[UserCtx, A => B] with AppContext {

  def mapUser[C](g: UserCtx => B => C):ContextualFunction[A,C] = ctxFn[A,C](u => f(u).andThen(g(u)))

  def mapTry[C](g: B => Try[C]):TryContextualFunction[A,C] = tryCtxFn[A,C](u => f(u).andThen(g))

  def apply(userCtx:UserCtx):A => B = {
    f(userCtx)(_)
  }

  def map[C](g:B => C):ContextualFunction[A,C] = ctxFn[A,C](u => f(u).andThen(g))

  def mapFuture[C](g: B => Future[C]):FutureContextualFunction[A,C] = futureCtxFn[A,C](u => f(u).andThen(g))
}

abstract class TryContextualFunction[A,B](f:UserCtx => A => Try[B]) extends ContextualFunction[A,Try[B]](f) {

  import ContextualFunction._
  def flatMap[C](g: B => Try[C]):TryContextualFunction[A,C] = {
    val g1:UserCtx => Try[B] => Try[C] = tryAdapter(u => b => {
      g(b) match {
        case s:Success[C] => s.value
        case f:Failure[C] => throw f.exception
      }
    })
    tryCtxFn[A,C](u => f(u).andThen(g1(u)))
  }

  def mapUserTry[C](g: UserCtx => B => C):TryContextualFunction[A,C] = {
    val g1:UserCtx => Try[B] => Try[C] = tryAdapter(g)
    tryCtxFn[A,C](u => f(u).andThen(g1(u)))
  }

  def map[C](g:B => C):TryContextualFunction[A,C] = mapUserTry(u => g)

  def mapTryFuture[C](g: B => Future[C]):FutureContextualFunction[A,C] = {
    val g1: Try[B] => Future[C] = tryB => {
      tryB match {
        case s:Success[B] => g(s.value)
        case f:Failure[B] => Promise[C].failure(f.exception).future
      }
    }

    futureCtxFn[A,C](u => f(u).andThen(g1))
  }
}

abstract class FutureContextualFunction[A,B](f:UserCtx => A => Future[B]) extends ContextualFunction[A,Future[B]](f) {

  import ContextualFunction._

  def flatMap[C](g: B => Future[C]) = {
    val g1:UserCtx => Future[B] => Future[C] = u => futureB => {
      val p = Promise[C]
      futureB.onComplete {
        case s:Success[B] => g(s.value).onComplete {
          case s:Success[C] => p.success(s.value)
          case f:Failure[C] => p.failure(f.exception)
        }
        case f:Failure[B] => p.failure(f.exception)
      }
      p.future
    }
    futureCtxFn[A,C](u => f(u).andThen(g1(u)))
  }

  def map[C](g:B => C):FutureContextualFunction[A,C] = {
    val g1: UserCtx => Future[B] => Future[C] = futureAdapter[B,C]((u:UserCtx) => g)
    futureCtxFn[A,C](u => f(u).andThen(g1(u)))
  }

  def mapUser[C](g: UserCtx => B => C):FutureContextualFunction[A,C] = {
    val g1 = futureAdapter(g)
    futureCtxFn[A,C](u => f(u).andThen(g1(u)))
  }

  private def adaptTry[C](g: B => Try[C]): Future[B] => Future[C] = {
    val p = Promise[C]
    b => b.onComplete {
      case s:Success[B] => g(s.value) match {
        case s:Success[C] => p.success(s.value)
        case f:Failure[C] => p.failure(f.exception)
      }
      case f:Failure[B] => f.exception
    }
      p.future
  }

  def mapTry[C](g: B => Try[C]):FutureContextualFunction[A,C] = {
    futureCtxFn[A,C](u => f(u).andThen(adaptTry[C](g)))
  }

}

case class UserCtx(token:String, u:User)
case class User(name:String, roles:Set[String] = Set.empty[String])



