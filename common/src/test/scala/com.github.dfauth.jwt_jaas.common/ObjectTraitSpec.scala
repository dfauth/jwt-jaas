package com.github.dfauth.jwt_jaas.common

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.matchers.{BeMatcher, MatchResult}
import org.scalatest.{FlatSpec, Matchers}

import scala.annotation.tailrec
import scala.concurrent.Await
import scala.util.{Failure, Success, Try}

class ObjectTraitSpec extends FlatSpec with Matchers with LazyLogging with TestAppContext {

  import scala.concurrent.duration._
  val timeout: Duration = 5.seconds


  import Blah._
  import CustomMatchers._

  "contextual functions" should "provide a framework to compose simpler functions" in {

    val f:ContextualFunction[Int,Int] = map(doubler).map(incrementer).mapUser[Int](userFactorApplier(mockUserFactorService)).map[Int](factorializer(mockFactorialService))

    f(new UserCtx("blah", new User("fred")))(1) should be (48)
    f(new UserCtx("blah", new User("wilma")))(1) should be (51)
  }

  "try contextual functions" should "provide a framework to compose simpler functions" in {

    val f1:TryContextualFunction[String,Int] = mapTry[String,Int](intifier).map(doubler).map(incrementer).map(stringifier).flatMap[Int](intifier)
    val f2:TryContextualFunction[String,Int] = f1.mapUserTry[Int](userFactorApplier(mockUserFactorService))
    val f3:TryContextualFunction[String,Int] = f2.map[Int](factorializer(mockFactorialService))

    f3(new UserCtx("blah", new User("fred")))("1") should be (Success(48))
    f3(new UserCtx("blah", new User("wilma")))("1") should be (Success(51))
    val input = "blah"
    f3(new UserCtx("blah", new User("wilma")))(input) should be (failure[Int](new NumberFormatException(s"""For input string: "${input}"""")))
  }

  "future contextual functions" should "provide a framework to compose simpler functions" in {

    val f1:FutureContextualFunction[Int,Int] = mapFuture[Int,Int](futurizer[Int]()).map(doubler).map(incrementer)
    val f2:FutureContextualFunction[Int,Int] = f1.mapUser[Int](userFactorApplier(mockUserFactorService)).flatMap[Int](futurizer(10l))
    val f3:FutureContextualFunction[Int,String] = f2.map[Int](factorializer(mockFactorialService)).map(stringifier)

    Await.result[String](f3(new UserCtx("blah", new User("fred")))(1), timeout) should be ("48")
    Await.result[String](f3(new UserCtx("blah", new User("wilma")))(1), timeout) should be ("51")
  }

  "future try contextual functions" should "provide a framework to compose simpler functions" in {

    val f1:FutureContextualFunction[String,Int] = mapFuture[String,String](futurizer[String]()).mapTry(intifier).map(doubler).map(incrementer)
    val f2:FutureContextualFunction[String,Int] = f1.mapUser[Int](userFactorApplier(mockUserFactorService)).flatMap[Int](futurizer(10l))
    val f3:FutureContextualFunction[String,String] = f2.map[Int](factorializer(mockFactorialService)).map(stringifier)

    Await.result[String](f3(new UserCtx("blah", new User("fred")))("1"), timeout) should be ("48")
    Await.result[String](f3(new UserCtx("blah", new User("wilma")))("1"), timeout) should be ("51")
    val input = "blah"
    val result = f3(new UserCtx("blah", new User("wilma")))(input)
    Await.ready(result, timeout)
    result.value.get should be (failure[String](new NumberFormatException(s"""For input string: "${input}"""")))
  }

  "try future contextual functions" should "provide a framework to compose simpler functions" in {

    val f1:FutureContextualFunction[String,Int] = mapTry(intifier).mapTryFuture[Int](futurizer[Int]()).map(doubler).map(incrementer)
    val f2:FutureContextualFunction[String,Int] = f1.mapUser[Int](userFactorApplier(mockUserFactorService)).flatMap[Int](futurizer(10l))
    val f3:FutureContextualFunction[String,String] = f2.map[Int](factorializer(mockFactorialService)).map(stringifier)

    Await.result[String](f3(new UserCtx("blah", new User("fred")))("1"), timeout) should be ("48")
    Await.result[String](f3(new UserCtx("blah", new User("wilma")))("1"), timeout) should be ("51")
    val input = "blah"
    val result = f3(new UserCtx("blah", new User("wilma")))(input)
    Await.ready(result, timeout)
    result.value.get should be (failure[String](new NumberFormatException(s"""For input string: "${input}"""")))
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
          case s:Success[T] => false
          case f:Failure[T] => {
            f.exception.getClass == t.getClass && f.exception.getMessage.equals(t.getMessage)
          }
        },
        left+" was not a failure or failed with an exception different to the expected exception",
        left+" was a failure"
      )
    }
  }
  def failure[T](t:Throwable) = new FailureMatcher[T](t)
}

// Make them easy to import with:
// import CustomMatchers._
object CustomMatchers extends CustomMatchers