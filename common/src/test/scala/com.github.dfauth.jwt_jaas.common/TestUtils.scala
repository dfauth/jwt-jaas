package com.github.dfauth.jwt_jaas.common

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.scalatest.matchers.{BeMatcher, MatchResult}
import spray.json.{DefaultJsonProtocol, JsonFormat, RootJsonFormat}

import scala.util.{Failure, Success, Try}

object TestUtils {

}

case class UserContext[T](token:String, payload:T)
case class Payload[T](payload:T)
case class Result[T](result:T)

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

