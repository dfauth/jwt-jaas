package dummies

import java.time.{Instant, LocalDateTime, ZoneId}

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

case class Credentials(userId:String, password:String)
case class MyUser(userId:String,
                  roles:Array[String],
                  expiry:Long = Instant.from(LocalDateTime.now.plusMinutes(15).atZone(ZoneId.systemDefault())).toEpochMilli,
                  refresh:Long = Instant.from(LocalDateTime.now.plusDays(1).atZone(ZoneId.systemDefault())).toEpochMilli
                 )

object CredentialsJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val credentialsFormat:RootJsonFormat[Credentials] = jsonFormat2(Credentials)
  implicit val myUserFormat:RootJsonFormat[MyUser] = jsonFormat4(MyUser)
}

