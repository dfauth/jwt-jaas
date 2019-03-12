package com.github.dfauth.jwt_jaas.kafka

import java.util.Collections

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import net.manub.embeddedkafka.EmbeddedKafka
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.connect.json.JsonDeserializer
import org.scalatest.{FlatSpec, Matchers}

class EmbeddedKafkaSpec
  extends FlatSpec
    with Matchers
    with EmbeddedKafka {

  val TOPIC = "testTopic"

  "runs with embedded kafka" should "work for strings" in {

    implicit val a = new StringDeserializer()

    withRunningKafka {
      publishStringMessageToKafka(TOPIC, "testMessage")
      val msg:String = consumeFirstMessageFrom(TOPIC)
      msg should be ("testMessage")
    }
  }

  "runs with embedded kafka" should "work for json" in {

    implicit val a = new JsonDeserializer()

    val node:JsonNode = new ObjectMapper().convertValue(Collections.singletonMap("key", "value"), classOf[JsonNode])

    withRunningKafka {
      publishStringMessageToKafka(TOPIC, node.toString)
      val msg:JsonNode = consumeFirstMessageFrom(TOPIC)
      msg should be (node)
    }
  }

}