package dummies

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

case class Credentials(userId:String, password:String)
case class MyUser(userId:String, roles:Array[String])

object CredentialsJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val credentialsFormat:RootJsonFormat[Credentials] = jsonFormat2(Credentials)
  implicit val myUserFormat:RootJsonFormat[MyUser] = jsonFormat2(MyUser)
}

