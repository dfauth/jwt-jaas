package com.github.dfauth.jws_jaas

import com.github.dfauth.jwt_jaas.kafka.JsonSupport._
import com.github.dfauth.jwt_jaas.kafka._
import com.typesafe.scalalogging.LazyLogging
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import spray.json._

class FactorialSpec extends FlatSpec
                    with Matchers
                    with EmbeddedKafka
                    with LazyLogging {


  "creating a microservice around a function" should "be simple" in {

    try {

      import com.github.dfauth.jws_jaas.JsonSupport.correlatableFormat

      withRunningKafkaOnFoundPort(EmbeddedKafkaConfig(kafkaPort = 9092, zooKeeperPort = 2181)) { implicit config =>
        val (zookeeperConnectString, brokerList) = connectionProperties(config)

        val microservice = MicroserviceFactory(
          (zookeeperConnectString, brokerList),
          config.customConsumerProperties, config.customProducerProperties)

        val f1:JsValue => Int = jsValue => jsValue match {
          case JsNumber(d) => d.toInt
          case _ => throw new RuntimeException("Oops")
        }

        val correlatableFormatter = correlatableFormat((i:Int) => JsNumber(i), f1)

        val endpoint = microservice.createMicroserviceEndpoint(
          "factorial",
          (p:Int) => factorial(p),
          (c:Correlatable[Int]) => correlatableFormatter.write(c).prettyPrint.getBytes,
          bytes => correlatableFormatter.read(JsonParser(bytes).asJsObject)
        )

        val f:Int => Future[Int] =
          MicroserviceFactory.createMicroserviceStub[Int, Int]("factorial",
          o => o.convertTo[Int],
          d => d.toJson,
          (zookeeperConnectString, brokerList),
          config.customProducerProperties, config.customProducerProperties)

        val p1:Future[Int] = f(3)
        val p2:Future[Int] = f(6)

        Await.result[Int](p2, 5.seconds) should be (720)
        Await.result[Int](p1, 5.seconds) should be (6)
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
