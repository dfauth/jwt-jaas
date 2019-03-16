package com.github.dfauth.jwt_jaas.kafka

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import org.apache.kafka.common.serialization.StringSerializer

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future



case class KafkaSink[V](topic: String,
                groupId: String = UUID.randomUUID().toString,
                zookeeperConnect: String = "localhost:6000",
                   brokerList:String = "localhost:6001", props:Map[String,Object] = Map.empty)
  extends LazyLogging {


  val props1:Map[String, Object] = props ++ Map[String, Object](
    "bootstrap.servers" -> brokerList,
//    "broker.list" -> brokerList,
    "key.serializer" -> classOf[StringSerializer],
    "value.serializer" -> classOf[StringSerializer],
    "client.id" -> groupId
  )

  private val producer = new org.apache.kafka.clients.producer.KafkaProducer[String, V](props1.asJava)

  def send(message: V):Future[RecordMetadata] = send(List(message)).find(a => true).get

  def send(messages: Seq[V]): Seq[Future[RecordMetadata]] = {
    val queueMessages = messages.map { message => new ProducerRecord[String, V](topic, message) }
    queueMessages.map(producer.send(_,(metadata: RecordMetadata, e: Exception) => {
      if(e == null) {
        logger.info(s"metadata: ${metadata}")
      } else {
        logger.error(e.getMessage,e)
      }
    })).map(v => Future(v.get()))
  }


}