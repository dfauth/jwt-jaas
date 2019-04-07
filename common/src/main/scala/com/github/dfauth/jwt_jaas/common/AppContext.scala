package com.github.dfauth.jwt_jaas.common

import scala.concurrent.Future
import scala.util.Try

trait AppContext {
  val factory:ContextualFunctionFactory
}

trait ContextualFunctionFactory {
    def ctxFn[A,B](f:UserCtx => A => B):ContextualFunction[A,B]
    def futureCtxFn[A,B](f:UserCtx => A => Future[B]):FutureContextualFunction[A,B]
    def tryCtxFn[A,B](f:UserCtx => A => Try[B]):TryContextualFunction[A,B]
}




