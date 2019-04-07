package com.github.dfauth.jwt_jaas.common

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Await
import scala.util.Success

// an example showing different serive implementations being mixed in
class ObjectTrait2Spec extends FlatSpec with Matchers with LazyLogging with TestAppContext2 {

  import scala.concurrent.duration._
  val timeout: Duration = 5.seconds


  import CustomMatchers._
  import TestAppContext1._

  "contextual functions" should "provide a framework to compose simpler functions" in {

    val f:ContextualFunction[Int,Int] = map(doubler).map(incrementer).mapUser[Int](userFactorApplier(userFactorService)).map[Int](factorializer(factorialService))

    f(new UserCtx("blah", new User("fred")))(1) should be (479001612)
    f(new UserCtx("blah", new User("wilma")))(1) should be (2004310031)
  }

  "contextual functions" should "also handle the case where a function can return a Try[T]" in {

    val f1:TryContextualFunction[String,Int] = mapTry[String,Int](intifier).map(doubler).map(incrementer).map(stringifier).flatMap[Int](intifier)
    val f2:TryContextualFunction[String,Int] = f1.mapUserTry[Int](userFactorApplier(userFactorService))
    val f3:TryContextualFunction[String,Int] = f2.map[Int](factorializer(factorialService))

    f3(new UserCtx("blah", new User("fred")))("1") should be (Success(479001612))
    f3(new UserCtx("blah", new User("wilma")))("1") should be (Success(2004310031))
    val input = "blah"
    f3(new UserCtx("blah", new User("wilma")))(input) should be (failure[Int](new NumberFormatException(s"""For input string: "${input}"""")))
  }

  "contextual functions" should "also handle the case where a function can return a Future[T]" in {

    val f1:FutureContextualFunction[Int,Int] = mapFuture[Int,Int](futurizer[Int]()).map(doubler).map(incrementer)
    val f2:FutureContextualFunction[Int,Int] = f1.mapUser[Int](userFactorApplier(userFactorService)).flatMap[Int](futurizer(10l))
    val f3:FutureContextualFunction[Int,String] = f2.map[Int](factorializer(factorialService)).map(stringifier)

    Await.result[String](f3(new UserCtx("blah", new User("fred")))(1), timeout) should be ("479001612")
    Await.result[String](f3(new UserCtx("blah", new User("wilma")))(1), timeout) should be ("2004310031")
  }

  "contextual functions" should "also handle the case where we combin both Try[T] and Future[T]" in {

    val f1:FutureContextualFunction[String,Int] = mapFuture[String,String](futurizer[String]()).mapTry(intifier).map(doubler).map(incrementer)
    val f2:FutureContextualFunction[String,Int] = f1.mapUser[Int](userFactorApplier(userFactorService)).flatMap[Int](futurizer(10l))
    val f3:FutureContextualFunction[String,String] = f2.map[Int](factorializer(factorialService)).map(stringifier)

    Await.result[String](f3(new UserCtx("blah", new User("fred")))("1"), timeout) should be ("479001612")
    Await.result[String](f3(new UserCtx("blah", new User("wilma")))("1"), timeout) should be ("2004310031")
    val input = "blah"
    val result = f3(new UserCtx("blah", new User("wilma")))(input)
    Await.ready(result, timeout)
    result.value.get should be (failure[String](new NumberFormatException(s"""For input string: "${input}"""")))
  }

  "contextual functions" should "also handle the case where we combin both Future[T] and Try[T] in any order" in {

    val f1:FutureContextualFunction[String,Int] = mapTry(intifier).mapTryFuture[Int](futurizer[Int]()).map(doubler).map(incrementer)
    val f2:FutureContextualFunction[String,Int] = f1.mapUser[Int](userFactorApplier(userFactorService)).flatMap[Int](futurizer(10l))
    val f3:FutureContextualFunction[String,String] = f2.map[Int](factorializer(factorialService)).map(stringifier)

    Await.result[String](f3(new UserCtx("blah", new User("fred")))("1"), timeout) should be ("479001612")
    Await.result[String](f3(new UserCtx("blah", new User("wilma")))("1"), timeout) should be ("2004310031")
    val input = "blah"
    val result = f3(new UserCtx("blah", new User("wilma")))(input)
    Await.ready(result, timeout)
    result.value.get should be (failure[String](new NumberFormatException(s"""For input string: "${input}"""")))
  }
}


