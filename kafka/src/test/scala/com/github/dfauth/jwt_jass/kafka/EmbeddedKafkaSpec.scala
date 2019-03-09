package com.github.dfauth.jwt_jass.kafka

import net.manub.embeddedkafka.EmbeddedKafka
import org.apache.kafka.common.serialization.StringDeserializer
import org.scalatest.{FlatSpec, Matchers}

class EmbeddedKafkaSpec
  extends FlatSpec
    with Matchers
    with EmbeddedKafka {

  val TOPIC = "testTopic"

  "runs with embedded kafka" should "work" in {

    implicit val a = new StringDeserializer()

    withRunningKafka {
      publishStringMessageToKafka(TOPIC, "testMessage")
      val msg:String = consumeFirstMessageFrom(TOPIC)
      msg should be ("testMessage")
    }
  }

}