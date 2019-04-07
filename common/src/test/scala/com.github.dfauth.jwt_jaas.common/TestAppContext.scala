package com.github.dfauth.jwt_jaas.common

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

// an application context demonstrating how to provide ContextualFunctionObjects
trait TestAppContext extends AppContext {

  // a function where the output depends on the user to test that the user content is correctly applied
  val userFactorService: UserFactorService

  // factorial service
  val factorialService: FactorialService

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

trait FactorialService {
  def factorial(i:Int):Int
}

trait UserFactorService {
  def forUser(u: User):Int => Int
}



