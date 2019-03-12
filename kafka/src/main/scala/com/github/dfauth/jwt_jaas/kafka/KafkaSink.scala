package com.github.dfauth.jwt_jaas.kafka

import java.util.{Properties, UUID}

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.serialization.StringSerializer

import scala.util.{Failure, Success, Try}


class KafkaSink[V](topic: String,
                groupId: String = UUID.randomUUID().toString,
                zookeeperConnect: String = "localhost:2181",
                   brokerList:String = "localhost:9092")
  extends LazyLogging {


  private val props = new Properties()

  props.put("bootstrap.servers", zookeeperConnect)
  props.put("broker.list", brokerList)
  props.put("producer.type", "async")
  props.put("batch.num.messages", "200")
//  props.put("metadata.broker.list", brokerList)
  props.put("message.send.max.retries", "5")
  props.put("request.required.acks", "-1")
  props.put("key.serializer", classOf[StringSerializer])
  props.put("value.serializer", classOf[StringSerializer])
  props.put("client.id", groupId)

  private val producer = new KafkaProducer[String, V](props)

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