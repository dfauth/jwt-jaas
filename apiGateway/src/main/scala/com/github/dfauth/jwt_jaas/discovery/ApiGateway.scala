package com.github.dfauth.jwt_jaas.discovery

import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{as, complete, entity, get, path, post}
import akka.http.scaladsl.server.RouteConcatenation._
import akka.http.scaladsl.server.{Route, RouteResult}
import com.github.dfauth.jwt_jaas.rest.RestEndPointServer
import com.typesafe.scalalogging.LazyLogging
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.hamcrest.Matchers.equalTo
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

object ApiGateway extends LazyLogging {

  def binder(binding: Http.ServerBinding) = {
    Binder(RestEndPointServer.endPointUrl(binding, "bind"))
  }

  implicit val materializer1 = RestEndPointServer.materializer
  implicit val system1 = RestEndPointServer.system

  val buffer:ListBuffer[(String,Route)] = ListBuffer()
  @volatile var state = Map.empty[String, Route]

  import JsonSupport._

  val bindRoute =
    path("bind") {
      post {
        entity(as[Binding]) { b =>

          val newRoute = path(b.path) {
            get {
              post {
                complete(HttpEntity(ContentTypes.`application/json`, """{"say":"new route"}"""))
              }
            }
          }

          buffer.append((b.path, newRoute))
          state = state ++ Map(b.path -> newRoute)
          complete(HttpEntity(ContentTypes.`application/json`, """{"bind":"ok"}"""))
        }
      }
    }
  val unbindRoute =
    path("unbind") {
      post {
        entity(as[Binding]) { b =>
          complete(HttpEntity(ContentTypes.`application/json`, """{"say":"hello"}"""))
        }
      }
    }

  val defaultRoute: Route = ctx => Future.successful[RouteResult.Complete](RouteResult.Complete(HttpResponse(StatusCodes.NotFound)))

  val dynamicRoutes: Route = ctx => {
    val routes = state.map { case (segment, route) =>
      get {
        complete(HttpEntity(ContentTypes.`application/json`, s"""{"route":"${segment}"}"""))
      }
    }
    concat(routes.toList: _*)(ctx)
  }

  // the concatenation must happen after declaration otherwise NPE!
  val routes:Route = bindRoute ~ unbindRoute ~ dynamicRoutes ~ defaultRoute
}


class ApiGateway(host:String = "localhost", port:Int = 8080) extends RestEndPointServer(ApiGateway.routes, host, port) {

}

case class Binding(path:String, url:String)

object JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit def bindingFormat:RootJsonFormat[Binding] = jsonFormat2(Binding)
}

case class Binder(bindUrl:String) {

  import JsonSupport._
  import spray.json._

  def bind(path: String, binding: Http.ServerBinding) = {
    val url = RestEndPointServer.endPointUrl(binding, path)
    val json:String = Binding(path, url).toJson.prettyPrint
    given().contentType(ContentType.JSON).
      body(json).log().all().
      post(bindUrl).
      then().log().body(true).
      statusCode(200).
      body("bind",equalTo("ok"));
  }

}