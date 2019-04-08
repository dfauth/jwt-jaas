package com.github.dfauth.jwt_jaas

import com.github.dfauth.jwt_jaas.kafka._
import com.typesafe.scalalogging.LazyLogging
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
    (a:A) => {
      val f1:Future[Correlatable[B]] = a match {
        case c:Correlatable[A] => f(c)
        case x => f(CorrelationEnvelope[A](payload = a))
      }
      f1.map(_.payload)
    }
  }

  import JsonSupport._
  import spray.json._

  private def parseJsValue[A](f: JsValue => A): JsValue => Correlatable[A] = {
    jsValue => parse[A](f)(jsValue)//Correlatable[A](f(jsValue))
  }

  private def formatJsValue[B](f: B => JsValue): Correlatable[B] => JsValue = {
    (b:Correlatable[B]) => format[B](f)(b)
  }

}

case class MicroserviceFactory(connectionProperties: (String, String),
                                    consumerProperties:Map[String, String],
                                    producerProperties:Map[String, String]
                                   ) extends LazyLogging {

  val (zookeeperConnectString, brokerList) = connectionProperties

  def createMicroserviceEndpoint[A,B](topicPrefix: String,
                                 f: A => B,
                                 serializer:Correlatable[B] => Array[Byte],
                                 deserializer:Array[Byte] => Correlatable[A]) = {

    val inboundTopic = MicroserviceFactory.inboundTopicName(topicPrefix)
    val outboundTopic = MicroserviceFactory.outboundTopicName(topicPrefix)

    val producer = KafkaProducerWrapper[Correlatable[B]](outboundTopic,
      new FunctionSerializer[Correlatable[B]](serializer),
      brokerList = brokerList,
      zookeeperConnect = zookeeperConnectString,
      props = producerProperties
    )

    val consumer = new KafkaConsumerWrapper[Correlatable[A]](inboundTopic,
      new FunctionDeserializer[Correlatable[A]](deserializer),
      brokerList = brokerList,
      zookeeperConnect = zookeeperConnectString,
      props = consumerProperties
    )

    consumer.subscribe((a:Correlatable[A]) => {
      val b = CorrelationEnvelope[B](a.correlationId, f(a.payload))
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
