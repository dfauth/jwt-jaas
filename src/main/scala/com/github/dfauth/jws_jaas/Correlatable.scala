package com.github.dfauth.jws_jaas

import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.github.dfauth.jwt_jaas.kafka._
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, RootJsonFormat}

trait Correlatable[T] {
  val correlationId:String
  val payload:T
}

case class CorrelatableContainer[T](
                                  override val correlationId: String = UUID.randomUUID().toString,
                                  override val payload: T
                                ) extends Correlatable[T]

object Correlatable {
  def apply[T](t:T):CorrelatableContainer[T] = CorrelatableContainer[T](payload = t)
  def apply[T](correlationId:String, t:T):CorrelatableContainer[T] = CorrelatableContainer[T](correlationId, t)
}

class CorrelationSerializer[T](f:CorrelatableContainer[T] => JsValue) extends JsValueSerializer[CorrelatableContainer[T]](f)

class CorrelationDeserializer[T](f:JsValue => CorrelatableContainer[T]) extends JsValueDeserializer[CorrelatableContainer[T]](f)

object JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit def correlatableFormat[T:JsonFormat]:RootJsonFormat[CorrelatableContainer[T]] = jsonFormat2(CorrelatableContainer.apply[T])
}

