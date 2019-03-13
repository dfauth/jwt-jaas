package com.github.dfauth.jwt_jaas.kafka

import java.util.{Collections, UUID}

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.common.serialization.StringDeserializer

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, TimeUnit}
import scala.concurrent.{Await, Future}



class KafkaSource[V](topic: String,
                  groupId: String = UUID.randomUUID().toString,
                  zookeeperConnect: String = "localhost:6000",
                     brokerList:String = "localhost:6001",
                     props:Map[String, Object] = Map.empty[String,Object])
  extends LazyLogging {

  private val TIMEOUT = 1000

  val props1:Map[String, Object] = props ++ Map(
//    "group.id" -> groupId,
    "bootstrap.servers" -> zookeeperConnect,
//    "broker.list" -> brokerList,
    "key.deserializer" -> classOf[StringDeserializer],
    "value.deserializer" -> classOf[StringDeserializer]
  )

  var closed = false

  private val connector = new KafkaConsumer[String,V](props1.asJava)


  def subscribe(action: java.util.function.Consumer[_ >: ConsumerRecord[String, V]]): Future[Unit] = {
    connector.subscribe(Collections.singleton(topic))
    Future[Unit] {
      while(!closed) {
        connector.poll(TIMEOUT).forEach(action)
      }
    }
  }

  def subscribe():ConsumerRecords[String, V] = {
    connector.subscribe(Collections.singleton(topic))
    connector.poll(TIMEOUT)
  }

  def close(f:Future[Unit], timeOut:Int, units:TimeUnit): Unit = {
    connector.close()
    closed = true
    Await.result(f, Duration(timeOut, units))
  }

}