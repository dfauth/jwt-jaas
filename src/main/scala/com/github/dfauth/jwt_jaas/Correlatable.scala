package com.github.dfauth.jwt_jaas

import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsObject, JsString, JsValue, JsonFormat, RootJsonFormat}

trait Correlatable[T] {
  val correlationId: String
  val payload:T
}

case class CorrelationEnvelope[T](
                           correlationId: String = UUID.randomUUID().toString,
                           payload: T
                                 ) extends Correlatable[T]

object Correlatable {
  def apply[T](t:T):Correlatable[T] = CorrelationEnvelope[T](UUID.randomUUID().toString, t)
}

object JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit def correlatableFormat[T:JsonFormat](formatT:T => JsValue, parseT:JsValue => T):RootJsonFormat[Correlatable[T]] = new RootJsonFormat[Correlatable[T]] {
    def write(correlatable: Correlatable[T]) = format[T](formatT)(correlatable)
    def read(value: JsValue):Correlatable[T] = parse[T](parseT)(value)
  }

  def parse[T](f:JsValue => T):JsValue => Correlatable[T] = jsValue => jsValue match {
    case j:JsObject => {
      val id:String = j.getFields("correlationId").map {
        case JsString(s) => s
      }.head
      val payload:T = j.getFields("payload").map(f(_)).head
      new CorrelationEnvelope[T](id, payload)
    }
    case _ => throw new RuntimeException("Oops")
  }
  def format[T](f:T => JsValue):Correlatable[T] => JsValue = correlatable => JsObject(Map[String, JsValue](
    "correlationId" -> JsString(correlatable.correlationId),
    "payload" -> f(correlatable.payload)
  ))

}

