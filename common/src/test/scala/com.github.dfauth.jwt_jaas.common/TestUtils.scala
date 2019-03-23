package com.github.dfauth.jwt_jaas.common

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsonFormat, RootJsonFormat}

object TestUtils {

}

case class UserContext[T](token:String, payload:T)
case class Payload[T](payload:T)
case class Result[T](result:T)


