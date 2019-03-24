package com.github.dfauth.jwt_jaas.common

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

object ContextualPipeline {

  def wrap[CTX,A,B](f:A => B):ContextualPipeline[CTX,A,B] = ctxWrap((user:CTX) => f)

  def ctxWrap[CTX,A,B](f:CTX => A => B):ContextualPipeline[CTX,A,B] = ContextualPipeline[CTX,A,B](f)

  def adaptFutureWithContext[CTX,A,B](f: CTX => A => B): CTX => Future[A] => Future[B] = {
    ctx => fa => fa.map(f(ctx)(_))
  }

  def adaptFuture[A,B](f: A => B): Future[A] => Future[B] = {
    fa => fa.map(f(_))
  }

}

case class ContextualPipeline[CTX,A,B](f:CTX => A => B)  {

  def map[C](g: B => C):ContextualPipeline[CTX,A,C] = mapWithContext((ctx:CTX) => g)

  def mapWithContext[C](g: CTX => B => C):ContextualPipeline[CTX,A,C] = {
    ContextualPipeline[CTX,A,C]((u:CTX) => f(u).andThen(g(u)))
  }

  def apply(u:CTX): A => B = f(u)
}



