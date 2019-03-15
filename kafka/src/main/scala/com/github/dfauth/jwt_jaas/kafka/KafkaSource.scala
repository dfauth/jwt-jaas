package com.github.dfauth.jwt_jaas.kafka

import java.util
import java.util.stream.{Collectors, StreamSupport}
import java.util.{Collections, UUID}

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer, OffsetAndMetadata}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{Deserializer, StringDeserializer}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, TimeUnit}
import scala.concurrent.{Await, Future}



class KafkaSource[V](topic: String,
                                           deserializer: Deserializer[V],
                  groupId: String = UUID.randomUUID().toString,
                  zookeeperConnect: String = "localhost:6000",
                     brokerList:String = "localhost:6001",
                     props:Map[String, Object] = Map.empty[String,Object])
  extends LazyLogging {

  private val TIMEOUT = 1000

  val props1:Map[String, Object] = props ++ Map(
    "group.id" -> groupId,
    "bootstrap.servers" -> brokerList,
//    "broker.list" -> brokerList,
//    "key.deserializer" -> classOf[StringDeserializer],
//    "value.deserializer" -> classOf[deserializer]
    "auto.offset.reset" -> "earliest",
    "enable.auto.commit" -> "false"
  )

  var closed = false

  private val consumer = new KafkaConsumer[String,V](props1.asJava, new StringDeserializer, deserializer)


  def subscribe(action: java.util.function.Consumer[_ >: ConsumerRecord[String, V]]): Future[Unit] = {
    val topics = Set(topic)
    consumer.subscribe(topics.asJava)
    topics.foreach(consumer.partitionsFor)
    Future[Unit] {
      while(!closed) {
        consumer.poll(TIMEOUT).forEach(action)
      }
    }
  }

  def getOneMessage():Option[V] = getMessages(1).find(_=>true)

  def getMessages(n:Int):Seq[V] = {
    val topics = Set(topic)
    consumer.subscribe(topics.asJava)
    topics.foreach(consumer.partitionsFor)
    val records:ConsumerRecords[String, V] = consumer.poll(TIMEOUT)

    val tmp = new util.ArrayList[V]()
    val it = records.iterator()
    while(it.hasNext) {
      val record = it.next()
      tmp.add(record.value())
      val tp = new TopicPartition(record.topic, record.partition)
      val om = new OffsetAndMetadata(record.offset + 1)
      consumer.commitSync(Map(tp -> om).asJava)
    }
//    val t:java.util.stream.Stream[V] = StreamSupport.stream[ConsumerRecord[String, V]](records.spliterator(), false).map[V](_.value) //
//    val c:java.util.List[V] = t.collect(Collectors.toList[V])
//    c.asScala
    tmp.asScala
  }

  def subscribe():java.util.Collection[V] = {
    consumer.subscribe(Collections.singleton(topic))
    val consumerRecords:ConsumerRecords[String, V] = consumer.poll(TIMEOUT)

    val t:java.util.stream.Stream[V] = StreamSupport.stream[ConsumerRecord[String, V]](consumerRecords.spliterator(), false).map[V](_.value) //
    val c:java.util.List[V] = t.collect(Collectors.toList[V])
    c
  }

  def close(f:Future[Unit], timeOut:Int, units:TimeUnit): Unit = {
    consumer.close()
    closed = true
    Await.result(f, Duration(timeOut, units))
  }

}