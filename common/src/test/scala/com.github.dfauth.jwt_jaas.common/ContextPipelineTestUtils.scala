package com.github.dfauth.jwt_jaas.common

import com.github.dfauth.jwt_jaas.ContextualPipeline.{adaptFuture, adaptFutureWithContext, wrap}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object ContextPipelineTestUtils extends LazyLogging {

  def compose(cache:Map[String, Double]): UserToken => Payload[String] => Result[Int] = {
    user => wrap(extractPayload).
      map[Int](toInt).
      mapWithContext(lookup(cache)).
      map(doubleToInt).
      map(toResult).
      apply(user)
  }

  def composeWithFuture: UserToken => Payload[String] => Future[Result[Int]] = {
    user => wrap(extractPayload).
      map[Int](toInt).
      map(toFuture).
      mapWithContext(adaptFutureWithContext(lookup(userFactorCache.cache))).
      map(adaptFuture(doubleToInt)).
      map(adaptFuture(toResult)).
      apply(user)
  }

  val extractPayload:Payload[String] => String  = { p =>
    logger.info(s"extractPayload: ${p}")
    p.payload
  }
  val toInt:String => Int = { s =>
    logger.info(s"toInt: ${s}")
    s.toInt
  }
  def lookup(cache:Map[String,Double]):UserToken => Int => Double  = { user =>
    logger.info(s"lookup: ${user}")
    k => k * cache.getOrElse(user.getUserId, 1.0)
  }
  val doubleToInt:Double => Int = { d =>
    logger.info(s"doubleToInt: ${d}")
    (d * 1000).toInt
  }
  val toResult:Int => Result[Int] = { i =>
    logger.info(s"toResult: ${i}")
    Result[Int](i)
  }
  def toFuture[T]:T => Future[T] = { t =>
    Future {
      Thread.sleep(500)
      logger.info(s"toFuture: ${t}")
      t
    }
  }

}

object userFactorCache {
  val cache = Map("fred" -> 2.0)
}
