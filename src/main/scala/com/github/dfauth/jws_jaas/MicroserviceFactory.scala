package com.github.dfauth.jws_jaas

import com.github.dfauth.jwt_jaas.kafka.{KafkaConsumerWrapper, KafkaProducerWrapper}
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.common.serialization.{Deserializer, Serializer}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

object MicroserviceFactory extends LazyLogging {

  def inboundTopicName(topicPrefix:String):String = s"${topicPrefix}.in"
  def outboundTopicName(topicPrefix:String):String = s"${topicPrefix}.out"

  def createMicroserviceStub[A, B](topicPrefix: String,
                                   deserializer:Deserializer[CorrelatableContainer[B]],
                                   serializer:Serializer[CorrelatableContainer[A]],
                                   connectionProperties: (String, String),
                                   consumerProperties:Map[String, String],
                                   producerProperties:Map[String, String]): A => Future[B] = {

    val inboundTopic = inboundTopicName(topicPrefix)
    val outboundTopic = outboundTopicName(topicPrefix)

    val (zookeeperConnectString, brokerList) = connectionProperties

    val producer = KafkaProducerWrapper[CorrelatableContainer[A]](inboundTopic,
      serializer,
      brokerList = brokerList,
      zookeeperConnect = zookeeperConnectString,
      props = producerProperties
    )

    val consumer = new KafkaConsumerWrapper[CorrelatableContainer[B]](outboundTopic,
      deserializer,
      brokerList = brokerList,
      zookeeperConnect = zookeeperConnectString,
      props = consumerProperties
    )

    // create an empty map
    var promises = scala.collection.mutable.Map[String, Promise[CorrelatableContainer[B]]]()

    val f:CorrelatableContainer[A] => Future[CorrelatableContainer[B]] = (a:CorrelatableContainer[A]) => {
      val p = Promise[CorrelatableContainer[B]]()
      promises += (a.correlationId -> p)
      producer.send(a).onComplete {
        case Success(r) => logger.info(s"${r}")
        case Failure(t) => logger.error(t.getMessage, t)
      }
      p.future
    }

    consumer.subscribe(b => {
      promises.remove(b.correlationId).map(p => p.success(b))
      Future {
        true
      }
    })
    (a:A) => f(Correlatable[A](a)).map(_.payload)
  }

}

case class MicroserviceFactory[A,B](topicPrefix: String,
                                    serializer:Serializer[CorrelatableContainer[B]],
                                    deserializer:Deserializer[CorrelatableContainer[A]],
                                    connectionProperties: (String, String),
                                    consumerProperties:Map[String, String],
                                    producerProperties:Map[String, String]
                                   ) extends LazyLogging {

  val (zookeeperConnectString, brokerList) = connectionProperties

  val inboundTopic = MicroserviceFactory.inboundTopicName(topicPrefix)
  val outboundTopic = MicroserviceFactory.outboundTopicName(topicPrefix)

  def createMicroserviceEndpoint(f: A => B) = {

    val producer = KafkaProducerWrapper[CorrelatableContainer[B]](outboundTopic,
      serializer,
      brokerList = brokerList,
      zookeeperConnect = zookeeperConnectString,
      props = producerProperties
    )

    val consumer = new KafkaConsumerWrapper[CorrelatableContainer[A]](inboundTopic,
      deserializer,
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
