package com.github.dfauth.jwt_jaas.kafka

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.serialization.StringSerializer

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}



class KafkaSink[V](topic: String,
                groupId: String = UUID.randomUUID().toString,
                zookeeperConnect: String = "localhost:6000",
                   brokerList:String = "localhost:6001", props:Map[String,Object] = Map.empty)
  extends LazyLogging {


  val props1:Map[String, Object] = props ++ Map[String, Object](
    "bootstrap.servers" -> zookeeperConnect,
//    "broker.list" -> brokerList,
    "key.serializer" -> classOf[StringSerializer],
    "value.serializer" -> classOf[StringSerializer],
//    "client.id" -> groupId
  )

  private val producer = new KafkaProducer[String, V](props1.asJava)

  def send(message: V): Try[Int] = send(List(message))

  def send(messages: Seq[V]): Try[Int] =
    try {
      val queueMessages = messages.map { message => new ProducerRecord[String, V](topic, message) }
      queueMessages.foreach(producer.send(_,new Callback(){
        override def onCompletion(metadata: RecordMetadata, exception: Exception): Unit = {

        }
      }))
      Success(queueMessages.size)
    } catch {
      case ex: Exception =>
        logger.error(ex.getMessage, ex)
        Failure(ex)
    }


}