package com.github.dfauth.jwt_jaas.kafka

import java.security.KeyPair
import java.time.{LocalDateTime, ZoneId}
import java.util.concurrent.{CountDownLatch, TimeUnit}

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.dfauth.jwt_jaas.jwt.{JWTBuilder, JWTVerifier, KeyPairFactory, User}
import com.typesafe.scalalogging.LazyLogging
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.scalatest.{FlatSpec, Matchers}
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global

class JwtUserContextSpec
  extends FlatSpec
    with Matchers
    with EmbeddedKafka
    with LazyLogging {

  val TOPIC = "testTopic"

  val keyPair: KeyPair = KeyPairFactory.createKeyPair("RSA", 2048)
  val jwtBuilder = new JWTBuilder("me",keyPair.getPrivate)
  val jwtVerifier = new JWTVerifier(keyPair.getPublic)

  "a function chain called by kafka" should "be secured by Jwt token passed in the message" in {

    import JsonSupport._

    try {

      withRunningKafkaOnFoundPort(EmbeddedKafkaConfig(kafkaPort = 9092, zooKeeperPort = 2181)) { implicit config =>
        val (zookeeperConnectString, brokerList) = connectionProperties(config)

        val producer = KafkaProducerWrapper[UserContext[Payload[String]]](TOPIC,
          UserContextSerializer(d => d.toJson),
          brokerList = brokerList,
          zookeeperConnect = zookeeperConnectString,
          props = config.customProducerProperties
        )

        val consumer = new KafkaConsumerWrapper[UserContext[Payload[String]]](TOPIC,
          new UserContextDeserializer(d => d.convertTo[UserContext[Payload[String]]]),
          brokerList = brokerList,
          zookeeperConnect = zookeeperConnectString,
          props = config.customConsumerProperties
        )

        val user = User.of("fred")
        val userSerialized = new ObjectMapper().writeValueAsString(user)
        val token = jwtBuilder.forSubject(userSerialized).withExpiry(LocalDateTime.now().plusMinutes(1).atZone(ZoneId.of("UTC"))).build()

        val latch = new CountDownLatch(1)
        consumer.subscribe(Utils.compose(jwtVerifier)(u => p => {
          logger.info(s"payload ${p} user: ${u}")
          latch.countDown()
        }))
        val usrCtx = UserContext(token,Payload("testMessage"))
        producer.send(usrCtx).onComplete(logSuccess)

        latch.await(20, TimeUnit.SECONDS)
        latch.getCount should be (0)
      }
    } catch {
      case e:RuntimeException => {
        logger.error(e.getMessage, e)
        throw e
      }
    } finally {
      // EmbeddedKafka.stop()
    }
  }

}






















