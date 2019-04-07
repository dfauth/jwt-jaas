package com.github.dfauth.jwt_jaas.common

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

// an application context demonstrating how to provide ContextualFunctionObjects
trait TestAppContext2 extends TestAppContext {

  def ctxFn[A,B](f:UserCtx => A => B):ContextualFunction[A,B] = new ContextualFunction[A,B](f) with TestAppContext2
  def futureCtxFn[A,B](f:UserCtx => A => Future[B]):FutureContextualFunction[A,B] = new FutureContextualFunction[A,B](f) with TestAppContext2
  def tryCtxFn[A,B](f:UserCtx => A => Try[B]):TryContextualFunction[A,B] = new TryContextualFunction[A,B](f) with TestAppContext2

  // a function where the output depends on the user to test that the user content is correctly applied
  val userFactorService: UserFactorService = u => i => u.name.length * i

  // mock factorial service
  val factorialService: FactorialService = (i: Int) => MyTestUtils.factorial(i)

}

object MyTestUtils {

  def recursiveFactorial(x: Int): Int = {

    @tailrec
    def factorialHelper(x: Int, accumulator: Int): Int = {
      if (x == 1) accumulator else factorialHelper(x - 1, accumulator * x)
    }
    factorialHelper(x, 1)
  }

  val factorial: Int => Int = recursiveFactorial
}

object TestAppContext2 extends TestAppContext2 {
  def mapFuture[A,B](f: A => Future[B]):FutureContextualFunction[A,B] = futureCtxFn[A,B](u => f)
  def mapTry[A,B](f: A => Try[B]):TryContextualFunction[A,B] = tryCtxFn[A,B](u => f)
  def map[A,B](f:A=>B) = ctxFn[A,B](user => f)
  def mapUser[A,B](f:UserCtx=>A=>B) = ctxFn[A,B](f)
}








