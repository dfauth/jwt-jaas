package com.github.dfauth.jws_jaas

import com.github.dfauth.jwt_jaas.kafka.{JsValueDeserializer, JsValueSerializer, KafkaConsumerWrapper, KafkaProducerWrapper}
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.common.serialization.{Deserializer, Serializer}
import spray.json.JsValue

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

object MicroserviceFactory extends LazyLogging {

  def inboundTopicName(topicPrefix:String):String = s"${topicPrefix}.in"
  def outboundTopicName(topicPrefix:String):String = s"${topicPrefix}.out"

  def createMicroserviceStub[A, B](topicPrefix: String,
                                   deserializer:JsValue => B,
                                   serializer:A => JsValue,
                                   connectionProperties: (String, String),
                                   consumerProperties:Map[String, String],
                                   producerProperties:Map[String, String]): A => Future[B] = {

    val inboundTopic = inboundTopicName(topicPrefix)
    val outboundTopic = outboundTopicName(topicPrefix)

    val (zookeeperConnectString, brokerList) = connectionProperties

    val producer = KafkaProducerWrapper[Correlatable[A]](inboundTopic,
      new JsValueSerializer[Correlatable[A]](formatJsValue(serializer)),
      brokerList = brokerList,
      zookeeperConnect = zookeeperConnectString,
      props = producerProperties
    )

    val consumer = new KafkaConsumerWrapper[Correlatable[B]](outboundTopic,
      new JsValueDeserializer[Correlatable[B]](parseJsValue(deserializer)),
      brokerList = brokerList,
      zookeeperConnect = zookeeperConnectString,
      props = consumerProperties
    )

    // create an empty map
    var promises = scala.collection.mutable.Map[String, Promise[Correlatable[B]]]()

    val f:Correlatable[A] => Future[Correlatable[B]] = (a:Correlatable[A]) => {
      val p = Promise[Correlatable[B]]()
      promises += (a.correlationId -> p)
      producer.send(a).onComplete {
        case Success(r) => logger.info(s"${r}")
        case Failure(t) => logger.error(t.getMessage, t)
      }
      p.future
    }

    consumer.subscribe(b => {
      val p1 = promises.remove(b.correlationId)
      p1.map(p =>
        p.success(b))
      Future {
        true
      }
    })
    (a:A) => f(Correlatable[A](a)).map(_.payload)
  }

  import com.github.dfauth.jws_jaas.JsonSupport._
  import spray.json._

  private def parseJsValue[A](f: JsValue => A): JsValue => Correlatable[A] = {
    jsValue => parse[A](f)(jsValue)//Correlatable[A](f(jsValue))
  }

  private def formatJsValue[B](f: B => JsValue): Correlatable[B] => JsValue = {
    (b:Correlatable[B]) => format[B](f)(b)
  }

}

case class MicroserviceFactory[A,B](serializer:B => JsValue,
                                    deserializer:JsValue => A,
                                    connectionProperties: (String, String),
                                    consumerProperties:Map[String, String],
                                    producerProperties:Map[String, String]
                                   ) extends LazyLogging {

  val (zookeeperConnectString, brokerList) = connectionProperties

  def createMicroserviceEndpoint(topicPrefix: String, f: A => B) = {

    val inboundTopic = MicroserviceFactory.inboundTopicName(topicPrefix)
    val outboundTopic = MicroserviceFactory.outboundTopicName(topicPrefix)

    val producer = KafkaProducerWrapper[Correlatable[B]](outboundTopic,
      new JsValueSerializer[Correlatable[B]](MicroserviceFactory.formatJsValue(serializer)),
      brokerList = brokerList,
      zookeeperConnect = zookeeperConnectString,
      props = producerProperties
    )

    val consumer = new KafkaConsumerWrapper[Correlatable[A]](inboundTopic,
      new JsValueDeserializer[Correlatable[A]](MicroserviceFactory.parseJsValue(deserializer)),
      brokerList = brokerList,
      zookeeperConnect = zookeeperConnectString,
      props = consumerProperties
    )

    consumer.subscribe((a:Correlatable[A]) => {
      val b = Correlatable[B](a.correlationId, f(a.payload))
      val p = Promise[Boolean]()
      producer.send(b).onComplete {
        case Success(recordMetadata) =>  p.success(true)
        case Failure(t) =>  p.failure(t)
      }
      p.future
    })
    (inboundTopic, outboundTopic)
  }

}
