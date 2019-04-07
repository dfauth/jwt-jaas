package com.github.dfauth.jwt_jaas.common

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

// an application context demonstrating how to provide ContextualFunctionObjects
trait TestAppContext1 extends TestAppContext {

  def ctxFn[A,B](f:UserCtx => A => B):ContextualFunction[A,B] = new ContextualFunction[A,B](f) with TestAppContext1
  def futureCtxFn[A,B](f:UserCtx => A => Future[B]):FutureContextualFunction[A,B] = new FutureContextualFunction[A,B](f) with TestAppContext1
  def tryCtxFn[A,B](f:UserCtx => A => Try[B]):TryContextualFunction[A,B] = new TryContextualFunction[A,B](f) with TestAppContext1

  // a function where the output depends on the user to test that the user content is correctly applied
  val userFactorService: UserFactorService = u => i => {
    if(u.name == "fred") {
      i*2
    } else {
      i*3
    }
  }

  // mock factorial service
  val factorialService: FactorialService = i => 42
}

object TestAppContext1 extends TestAppContext1 {
  def mapFuture[A,B](f: A => Future[B]):FutureContextualFunction[A,B] = futureCtxFn[A,B](u => f)
  def mapTry[A,B](f: A => Try[B]):TryContextualFunction[A,B] = tryCtxFn[A,B](u => f)
  def map[A,B](f:A=>B) = ctxFn[A,B](user => f)
  def mapUser[A,B](f:UserCtx=>A=>B) = ctxFn[A,B](f)
}






