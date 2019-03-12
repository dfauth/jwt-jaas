package com.github.dfauth.jwt_jaas.kafka

import java.util.{Collections, Properties, UUID}

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.common.serialization.StringDeserializer

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, TimeUnit}
import scala.concurrent.{Await, Future}

class KafkaSource[V](topic: String,
                  groupId: String = UUID.randomUUID().toString,
                  zookeeperConnect: String = "localhost:2181",
                     brokerList:String = "localhost:9092")
  extends LazyLogging {

  private val TIMEOUT = 1000
  private val props = new Properties()

  props.put("group.id", groupId)
  props.put("bootstrap.servers", zookeeperConnect)
  props.put("broker.list", brokerList)
  // props.put("auto.offset.reset", "smallest")
  //2 minute consumer timeout
  props.put("consumer.timeout.ms", "120000")
  //commit after each 10 second
  props.put("auto.commit.interval.ms", "10000")
  props.put("key.deserializer", classOf[StringDeserializer])
  props.put("value.deserializer", classOf[StringDeserializer])

  var closed = false

  private val connector = new KafkaConsumer[String,V](props)


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