package com.github.dfauth.jwt_jaas.common

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

trait TestAppContext extends AppContext {

  def ctxFn[A,B](f:UserCtx => A => B):ContextualFunction[A,B] = new ContextualFunction[A,B](f) with TestAppContext
  def futureCtxFn[A,B](f:UserCtx => A => Future[B]):FutureContextualFunction[A,B] = new FutureContextualFunction[A,B](f) with TestAppContext
  def tryCtxFn[A,B](f:UserCtx => A => Try[B]):TryContextualFunction[A,B] = new TryContextualFunction[A,B](f) with TestAppContext

  // a function where the output depends on the user to test that the user content is correctly applied
  val mockUserFactorService: UserFactorService = u => i => {
    if(u.name == "fred") {
      i*2
    } else {
      i*3
    }
  }

  // mock factorial service
  val mockFactorialService: FactorialService = i => 42

  // given a set of functions
  val doubler:Int => Int = i => i*2
  val incrementer:Int => Int = i => i+1
  val stringifier:Int => String = i => i.toString
  val intifier:String => Try[Int] = s => Try {s.toInt}
  val userFactorApplier:UserFactorService => UserCtx => Int => Int = s => u => i => s.forUser(u.u)(i)
  val factorializer:FactorialService => Int => Int = s => i => i + s.factorial(i)
  def futurizer[T](l:Long = 500l):T => Future[T] = t => Future {
    Thread.sleep(l)
    t
  }

}

object TestAppContext extends TestAppContext {
  def mapFuture[A,B](f: A => Future[B]):FutureContextualFunction[A,B] = futureCtxFn[A,B](u => f)
  def mapTry[A,B](f: A => Try[B]):TryContextualFunction[A,B] = tryCtxFn[A,B](u => f)
  def map[A,B](f:A=>B) = ctxFn[A,B](user => f)
  def mapUser[A,B](f:UserCtx=>A=>B) = ctxFn[A,B](f)
}

trait UserFactorService {
  def forUser(u: User):Int => Int
}



