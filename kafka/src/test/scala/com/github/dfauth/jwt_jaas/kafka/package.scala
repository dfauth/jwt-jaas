package com.github.dfauth.jwt_jaas

import com.typesafe.scalalogging.LazyLogging
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.apache.kafka.clients.producer.RecordMetadata

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

package object kafka extends LazyLogging {

  def connectionProperties(config: EmbeddedKafkaConfig):Tuple2[String,String] = (s"localhost:${config.zooKeeperPort}",s"localhost:${config.kafkaPort}")

  val logSuccess: Try[RecordMetadata] => Unit = t => t match {
    case Success(r) => {
      logger.info(s"success: ${r}")
    }
    case Failure(f) => {
      logger.error(f.getMessage, f)
    }
  }

  def recursiveFactorial(x: Int): Int = {

    @tailrec
    def factorialHelper(x: Int, accumulator: Int): Int = {
      if (x == 1) accumulator else factorialHelper(x - 1, accumulator * x)
    }
    factorialHelper(x, 1)
  }

  val factorial: Int => Int = recursiveFactorial

}
