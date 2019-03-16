package com.github.dfauth.jwt_jaas.kafka

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.producer.RecordMetadata

import scala.collection.JavaConverters._
import scala.concurrent.Future


case class KafkaProducerWrapper[V](topic: String,
                                   groupId: String = UUID.randomUUID().toString,
                                   zookeeperConnect: String = "localhost:6000",
                                   brokerList:String = "localhost:6001",
                                   props:Map[String,Object] = Map.empty) extends LazyLogging {
  val producer = new KafkaProducer[V](topic, groupId, zookeeperConnect, brokerList, props.asJava)

  def send(messages: Seq[V]):Seq[Future[RecordMetadata]] = {
    producer.send(messages.asJava).asScala.toSeq.map(f => Future {
      f.get()
    })
  }

  def send(message: V):Future[RecordMetadata] = {
    send(Seq(message)).find(_ => true).getOrElse(Future.failed(new RuntimeException("Oops")))
  }
}
