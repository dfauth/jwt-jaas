package com.github.dfauth.jwt_jaas.rest

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

case class Payload(payload:String)
case class Result[T](result:T)

object JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val payloadFormat:RootJsonFormat[Payload] = jsonFormat1(Payload)
  implicit val resultFormat:RootJsonFormat[Result[String]] = jsonFormat1(Result[String])
  implicit val intResultFormat:RootJsonFormat[Result[Int]] = jsonFormat1(Result[Int])
}


