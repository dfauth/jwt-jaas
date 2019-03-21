package com.github.dfauth.jwt_jaas

import akka.http.scaladsl.server.Route
import com.github.dfauth.jwt_jaas.ContextualPipeline._
import com.github.dfauth.jwt_jaas.jwt.User
import com.typesafe.scalalogging.LazyLogging
import io.restassured.http.ContentType
import io.restassured.response.Response
import org.hamcrest.Matchers._
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class DependencyInjectionSpec extends FlatSpec with Matchers with LazyLogging {

  "any authenticated get endpoint" should "be able to execute a function taking the user as well as a parameter" in {

    import TestUtils._
    import akka.http.scaladsl.server.Directives._
    import com.github.dfauth.jwt_jaas.JsonSupport._
    import com.github.dfauth.jwt_jaas.Routes._
    import spray.json._

    val compose:User => String => Result[Int] = {
      user => (s:String) => Result(s.toInt)
    }

    val routes:Route = login(handle) ~ genericGet1Endpoint(compose)(intResultFormat)

    val endPoint = RestEndPointServer(routes, port = 0)
    val bindingFuture = endPoint.start()
    val binding = Await.result(bindingFuture, 5.seconds)
    implicit val loginEndpoint:String = endPoint.endPointUrl(binding, "login")

    try {
      val userId:String = "fred"
      val password:String = "password"
      val tokens:Tokens = asUser(userId).withPassword(password).login

      val payload = "2"
      val bodyContent:String = Payload(payload).toJson.prettyPrint

      val response:Response = tokens.when.log().all().
        contentType(ContentType.JSON).
        get(endPoint.endPointUrl(binding, "endpoint/{payload}"), payload)

      response.then().log.all.statusCode(200).
        body("result",equalTo(payload.toInt))
    } finally {
      endPoint.stop(bindingFuture)
    }
  }

  "any authenticated post endpoint" should "be able to execute a function taking the user as well as a parameter" in {

    import TestUtils._
    import akka.http.scaladsl.server.Directives._
    import com.github.dfauth.jwt_jaas.JsonSupport._
    import com.github.dfauth.jwt_jaas.Routes._
    import spray.json._

    val compose:User => Payload => Result[Int] = user => (p:Payload) => Result(p.payload.toInt)

    val routes:Route = login(handle) ~ genericPostEndpoint(compose)

    val endPoint = RestEndPointServer(routes, port = 0)
    val bindingFuture = endPoint.start()
    val binding = Await.result(bindingFuture, 5.seconds)
    implicit val loginEndpoint:String = endPoint.endPointUrl(binding, "login")

    try {
      val userId:String = "fred"
      val password:String = "password"
      val tokens:Tokens = asUser(userId).withPassword(password).login

      val payload = "2"
      val bodyContent:String = Payload(payload).toJson.prettyPrint

      val response:Response = tokens.when.log().all().
        contentType(ContentType.JSON).
        body(bodyContent).
        post(endPoint.endPointUrl(binding, "endpoint"))

      response.then().statusCode(200).
        body("result",equalTo(payload.toInt))
    } finally {
      endPoint.stop(bindingFuture)
    }
  }

  "any authenticated post endpoint" should "be able to propagate its user information though a pipeline of chained functions" in {

    import TestUtils._
    import akka.http.scaladsl.server.Directives._
    import com.github.dfauth.jwt_jaas.JsonSupport._
    import com.github.dfauth.jwt_jaas.Routes._
    import spray.json._

    val cache = Map("fred" -> 2.0)
    val routes:Route = login(handle) ~ genericPostEndpoint(compose(cache))

    val endPoint = RestEndPointServer(routes, port = 0)
    val bindingFuture = endPoint.start()

    val binding = Await.result(bindingFuture, 5.seconds)
    implicit val loginEndpoint:String = endPoint.endPointUrl(binding, "login")

    try {
      val userId:String = "fred"
      val password:String = "password"
      val tokens:Tokens = asUser(userId).withPassword(password).login

      val payload = "2"
      val bodyContent:String = Payload(payload).toJson.prettyPrint

      val response:Response = tokens.when.log().all().
        contentType(ContentType.JSON).
        body(bodyContent).
        post(endPoint.endPointUrl(binding, "endpoint"))

      response.then().log.all.statusCode(200).
        body("result",equalTo( (cache(userId)*1000).toInt*payload.toInt))
    } finally {}
    try {
      val userId:String = "wilma"
      val password:String = "password"
      val tokens:Tokens = asUser(userId).withPassword(password).login

      val payload = "2"
      val bodyContent:String = Payload(payload).toJson.prettyPrint

      val response:Response = tokens.when.log().all().
        contentType(ContentType.JSON).
        body(bodyContent).
        post(endPoint.endPointUrl(binding, "endpoint"))

      response.then().log.all.statusCode(200).
        body("result",equalTo(1000*payload.toInt))
    } finally {
      endPoint.stop(bindingFuture)
    }
  }

  "any authenticated post endpoint" should "be able to propagate its user information though a pipeline of chained functions including futures" in {

    import TestUtils._
    import akka.http.scaladsl.server.Directives._
    import com.github.dfauth.jwt_jaas.JsonSupport._
    import com.github.dfauth.jwt_jaas.Routes._
    import spray.json._

    val routes:Route = login(handle) ~ genericPostFutureEndpoint(composeWithFuture)

    val endPoint = RestEndPointServer(routes, port = 0)
    val bindingFuture = endPoint.start()
    val binding = Await.result(bindingFuture, 5.seconds)
    implicit val loginEndpoint:String = endPoint.endPointUrl(binding, "login")

    try {
      val userId:String = "fred"
      val password:String = "password"
      val tokens:Tokens = asUser(userId).withPassword(password).login

      val payload = "2"
      val bodyContent:String = Payload(payload).toJson.prettyPrint

      val response:Response = tokens.when.log().all().
        contentType(ContentType.JSON).
        body(bodyContent).
        post(endPoint.endPointUrl(binding, "endpoint"))

      response.then().log.all.statusCode(200).
        body("result",equalTo( (userFactorCache.cache(userId)*1000).toInt*payload.toInt))
    } finally {
      endPoint.stop(bindingFuture)
    }
  }

  def compose(cache:Map[String, Double]): User => Payload => Result[Int] = {
    user => wrap(extractPayload).
            map[Int](toInt).
            mapWithContext(lookup(cache)).
            map(doubleToInt).
            map(toResult).
            apply(user)
  }

  def composeWithFuture: User => Payload => Future[Result[Int]] = {
    user => wrap(extractPayload).
            map[Int](toInt).
            map(toFuture).
            mapWithContext(adaptFutureWithContext(lookup(userFactorCache.cache))).
            map(adaptFuture(doubleToInt)).
            map(adaptFuture(toResult)).
            apply(user)
  }

  val extractPayload:Payload => String  = { p =>
    logger.info(s"extractPayload: ${p}")
    p.payload
  }
  val toInt:String => Int = { s =>
    logger.info(s"toInt: ${s}")
    s.toInt
  }
  def lookup(cache:Map[String,Double]):User => Int => Double  = { user =>
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

  object userFactorCache {
    val cache = Map("fred" -> 2.0)
  }

}




















