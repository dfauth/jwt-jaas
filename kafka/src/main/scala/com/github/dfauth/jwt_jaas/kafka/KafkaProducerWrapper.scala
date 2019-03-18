package com.github.dfauth.jwt_jaas.kafka

import java.lang
import java.util.UUID
import java.util.concurrent.CompletableFuture

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.Deserializer

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}


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

case class KafkaConsumerWrapper[V](topic: String,
                                   deserializer:Deserializer[V],
                                   groupId: String = UUID.randomUUID().toString,
                                   zookeeperConnect: String = "localhost:6000",
                                   brokerList:String = "localhost:6001",
                                   props:Map[String,Object] = Map.empty
                                   ) extends LazyLogging {

  val consumer:KafkaConsumer[V] = new KafkaConsumer[V](topic, groupId, zookeeperConnect, brokerList, props.asJava, deserializer)

  def getMessages(n: Int, timeout:Int = 5000): Seq[V] = {
    consumer.getMessages(n, timeout).asScala.toSeq
  }

  def getOneMessage(timeout:Int = 5000):Option[V] = getMessages(1, timeout).find(_ => true)

  def subscribe(f: V => Future[Boolean]): Unit = {

    consumer.subscribe((t: V) => {
      val tmp = new CompletableFuture[lang.Boolean]()
      f(t).onComplete {
        case Success(b) => tmp.complete(b)
        case Failure(t) => tmp.completeExceptionally(t)
      }
      tmp
    })
  }
}

