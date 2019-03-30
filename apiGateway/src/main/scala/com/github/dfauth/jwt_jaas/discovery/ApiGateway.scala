package com.github.dfauth.jwt_jaas.discovery

import java.net.URL

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{as, complete, entity, extract, get, path, post}
import akka.http.scaladsl.server.RouteConcatenation._
import akka.http.scaladsl.server.{Route, RouteResult}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.github.dfauth.jwt_jaas.rest.RestEndPointServer
import com.typesafe.scalalogging.LazyLogging
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.hamcrest.Matchers.equalTo
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.Future

object ApiGateway extends LazyLogging {

  def binder(binding: Http.ServerBinding) = {
    Binder(RestEndPointServer.endPointUrl(binding, "bind"))
  }

  implicit val system = ActorSystem("apiGateway")
  implicit val materializer = ActorMaterializer()

  @volatile var state = Map.empty[String, Route]

  def bind(b: Binding) = {
    val newRoute: Route = {
      val url = b.getURL()
      val flow = Http().outgoingConnection(url.getHost, url.getPort)
      path(b.path) {
        get {
          extract(_.request) { req ⇒
            val futureResponse = Source.single(req).via(flow).runWith(Sink.head)
            complete(futureResponse)
          }
        }
      }
    }

    state = state ++ Map(b.path -> newRoute)
  }

  import JsonSupport._

  val bindRoute =
    path("bind") {
      post {
        entity(as[Binding]) { b => bind(b)
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
    val routes = state.map {
      case (segment, route) => route
    }
    concat(routes.toList: _*)(ctx)
  }

  // the concatenation must happen after declaration otherwise NPE!
  val routes:Route = bindRoute ~ unbindRoute ~ dynamicRoutes ~ defaultRoute
}


class ApiGateway(host:String = "localhost", port:Int = 8080) extends RestEndPointServer(ApiGateway.routes, host, port) {

  def bind(b: Binding) = ApiGateway.bind(b)

}

case class Binding(path:String, url:String) {
  def getURL() = new URL(url)
}

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