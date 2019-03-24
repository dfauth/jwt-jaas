package com.github.dfauth.jwt_jaas.kafka

import java.util

import org.apache.kafka.common.serialization.Serializer
import spray.json.JsValue

abstract class JsValueSerializer[T](f:T => JsValue) extends Serializer[T] {

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {}

  override def close(): Unit = {}

  override def serialize(topic: String, data: T): Array[Byte] = {
    f(data).prettyPrint.getBytes
  }
}