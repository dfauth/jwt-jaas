package com.github.dfauth.jwt_jaas.common

import com.github.dfauth.jwt_jaas.common
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.matchers.{BeMatcher, MatchResult}
import org.scalatest.{FlatSpec, Matchers}

import scala.annotation.tailrec
import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

class ObjectTraitSpec extends FlatSpec with Matchers with LazyLogging {

  import scala.concurrent.duration._
  val timeout: Duration = 5.seconds

  val mockUserFactorService: UserFactorService = u => i => {
    if(u.name == "fred") {
      i*2
    } else {
      i*3
    }
  }

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

  import ContextualFunction._
  import CustomMatchers._

  "contextual functions" should "provide a framework to compose simpler functions" in {

    val f:ContextualFunction[Int,Int] = mapSimple(doubler).mapSimple(incrementer).mapUser[Int](userFactorApplier(mockUserFactorService)).mapSimple[Int](factorializer(mockFactorialService))

    f(new UserCtx("blah", new User("fred")))(1) should be (48)
    f(new UserCtx("blah", new User("wilma")))(1) should be (51)
  }

  "try contextual functions" should "provide a framework to compose simpler functions" in {

    val f1:TryContextualFunction[String,Int] = mapTry[String,Int](intifier).mapSimple(doubler).mapSimple(incrementer).mapSimple(stringifier).flatMap[Int](intifier)
    val f2:TryContextualFunction[String,Int] = f1.mapUserTry[Int](userFactorApplier(mockUserFactorService))
    val f3:TryContextualFunction[String,Int] = f2.mapSimple[Int](factorializer(mockFactorialService))

    f3(new UserCtx("blah", new User("fred")))("1") should be (Success(48))
    f3(new UserCtx("blah", new User("wilma")))("1") should be (Success(51))
    val input = "blah"
    f3(new UserCtx("blah", new User("wilma")))(input) should be (failure[Int](new NumberFormatException(s"""For input string: "${input}"""")))
  }

  "future contextual functions" should "provide a framework to compose simpler functions" in {

    val f1:FutureContextualFunction[Int,Int] = mapFuture[Int,Int](futurizer[Int]()).mapSimple(doubler).mapSimple(incrementer)
    val f2:FutureContextualFunction[Int,Int] = f1.mapUser[Int](userFactorApplier(mockUserFactorService)).flatMap[Int](futurizer(10l))
    val f3:FutureContextualFunction[Int,String] = f2.mapSimple[Int](factorializer(mockFactorialService)).mapSimple(stringifier)

    Await.result[String](f3(new UserCtx("blah", new User("fred")))(1), timeout) should be ("48")
    Await.result[String](f3(new UserCtx("blah", new User("wilma")))(1), timeout) should be ("51")
  }
}

trait UserFactorService {
  def forUser(u: User):Int => Int
}

trait AppContext {
}

trait TestAppContext extends AppContext {
}

trait ProdAppContext extends AppContext {
}

case class UserCtx(token:String, u:User)
case class User(name:String, roles:Set[String] = Set.empty[String])

object ContextualFunction extends LazyLogging {

  def mapFuture[A,B](f: A => Future[B]):FutureContextualFunction[A,B] = new FutureContextualFunction[A,B](u => f) {}

  def mapTry[A,B](f: A => Try[B]):TryContextualFunction[A,B] = new TryContextualFunction[A,B](u => f) {}

  def mapSimple[A,B,S](f:A=>B) = new SimpleContextualFunction[A,B](f) with TestAppContext

  def mapUser[A,B](f:UserCtx=>A=>B) = new UserContextualFunction[A,B](f) with TestAppContext

  def map[A,B](f:UserCtx=>A=>B) = new ContextualFunction[A,B](f) with TestAppContext

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

  def mapUser[C](g: UserCtx => B => C):ContextualFunction[A,C] = new ContextualFunction[A,C](u => f(u).andThen(g(u))) {}

  def mapTry[C](g: B => Try[C]):TryContextualFunction[A,C] = new TryContextualFunction[A,C](u => f(u).andThen(g)) {}

  def apply(userCtx:UserCtx):A => B = {
    f(userCtx)(_)
  }

  def mapSimple[C](g:B => C):ContextualFunction[A,C] = new ContextualFunction[A,C](u => f(u).andThen(g)) {}

}

abstract class UserContextualFunction[A,B](f:UserCtx => A => B) extends ContextualFunction[A,B](u => a => f(u)(a))

abstract class SimpleContextualFunction[A,B](f:A => B) extends UserContextualFunction[A,B](u => a => f(a))

abstract class TryContextualFunction[A,B](f:UserCtx => A => Try[B]) extends UserContextualFunction[A,Try[B]](f) {

  import ContextualFunction._
  def mapFuture[C](g: B => Future[C]): FutureContextualFunction[A, C] = {
    //val g1:UserCtx => Try[B] => Try[C] = tryAdapter(u => g)
    // new FutureContextualFunction[A,C](u => f(u).andThen(futureAdapter(g)))
    null
  }


  import ContextualFunction._
  def flatMap[C](g: B => Try[C]):TryContextualFunction[A,C] = {
    val g1:UserCtx => Try[B] => Try[C] = tryAdapter(u => b => {
      g(b) match {
        case s:Success[C] => s.value
        case f:Failure[C] => throw f.exception
      }
    })
    new TryContextualFunction[A,C](u => f(u).andThen(g1(u))) {}
  }

  def mapUserTry[C](g: UserCtx => B => C):TryContextualFunction[A,C] = {
    val g1:UserCtx => Try[B] => Try[C] = tryAdapter(g)
    new TryContextualFunction[A,C](u => f(u).andThen(g1(u))){}
  }

  def mapSimple[C](g:B => C):TryContextualFunction[A,C] = mapUserTry(u => g)
}

abstract class FutureContextualFunction[A,B](f:UserCtx => A => Future[B]) extends UserContextualFunction[A,Future[B]](f) {

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
    new FutureContextualFunction[A,C](u => f(u).andThen(g1(u))) {}
  }

  def mapSimple[C](g:B => C):FutureContextualFunction[A,C] = {
    val g1: UserCtx => Future[B] => Future[C] = futureAdapter[B,C]((u:UserCtx) => g)
    new FutureContextualFunction[A,C](u => f(u).andThen(g1(u))) {}
  }

  def mapUser[C](g: UserCtx => B => C):FutureContextualFunction[A,C] = {
    val g1 = futureAdapter(g)
    new FutureContextualFunction[A,C](u => f(u).andThen(g1(u))) {}
  }

  def flatMapTry[C](g: B => Try[C]):TryContextualFunction[A,C] = {
    //new TryContextualFunction[A,C](u => f(u).andThen(g)) {}
    null
  }

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

trait FactorialService {
  def factorial(i:Int):Int
}

case class FactorialServiceImpl() extends FactorialService {
  override def factorial(i: Int): Int = factorial(i)
}

case class MockFactorialService() extends FactorialService {
  override def factorial(i: Int): Int = 42
}

trait CustomMatchers {

  class FailureMatcher[T](t:Throwable) extends BeMatcher[Try[T]] {
    override def apply(left: Try[T]): MatchResult = {
      MatchResult(
        left match {
          case f:Failure[T] => {
            f.exception.getClass == t.getClass && f.exception.getMessage.equals(t.getMessage)
          }
        },
        left+" was not a failure",
        left+" was a failure"
      )
    }
  }
  def failure[T](t:Throwable) = new FailureMatcher[T](t)
}

// Make them easy to import with:
// import CustomMatchers._
object CustomMatchers extends CustomMatchers