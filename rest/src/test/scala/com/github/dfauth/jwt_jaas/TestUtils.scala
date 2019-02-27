package com.github.dfauth.jwt_jaas

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.github.dfauth.jwt_jaas.CredentialsJsonSupport._
import com.github.dfauth.jwt_jaas.jwt.{Role, User}
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import io.restassured.response.Response
import io.restassured.specification.RequestSpecification
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

object TestUtils {

  def handle(credentials:Credentials): Option[User]= {
    credentials match {
      case Credentials("fred", "password") => Some(User.of("fred", Role.role("admin"), Role.role("user")))
      case Credentials("wilma", "password") => Some(User.of("wilma", Role.role("user")))
      case _ => None
    }
  }
  def asUser(userId: String)(implicit endpoint:String):LoginBuilder = new LoginBuilder(endpoint,userId)
}

class LoginBuilder(endpoint:String, userId:String) {
  def withPassword(password:String): CredentialsBuilder = new CredentialsBuilder(endpoint, userId, password)
}

class CredentialsBuilder(endpoint:String, userId:String, password:String) {
  def login:Tokens = {

    import spray.json._
    val bodyContent:String = Credentials(userId, password).toJson.prettyPrint
    val response:Response = given().when().log().all().contentType(ContentType.JSON).body(bodyContent).post(endpoint)

    response.then().statusCode(200)
    val authorizationToken = response.body.path[String]("authorizationToken")
    assert(authorizationToken != null)
    assert(authorizationToken.length > 0)
    val refreshToken = response.body.path[String]("refreshToken")
    assert(refreshToken != null)
    assert(refreshToken.length > 0)
    assert(!refreshToken.equals(authorizationToken))
    Tokens(authorizationToken, refreshToken)
  }
}

case class Tokens(authorizationToken:String, refreshToken:String) {
  def when():RequestSpecification = given().header("Authorization", "Bearer "+authorizationToken).when()
}

case class Payload(payload:String)
case class Result[T](result:T)

object JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val payloadFormat:RootJsonFormat[Payload] = jsonFormat1(Payload)
  implicit val resultFormat:RootJsonFormat[Result[String]] = jsonFormat1(Result[String])
  implicit val intResultFormat:RootJsonFormat[Result[Int]] = jsonFormat1(Result[Int])
}


