package com.github.dfauth.jwt_jaas.rest

import java.io.StringWriter
import java.net.URI
import java.util
import java.util.Collections
import java.util.function.Consumer

import com.github.dfauth.jwt_jaas.jwt.{Role, User}
import com.github.dfauth.jwt_jaas.rest.CredentialsJsonSupport._
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import io.restassured.response.Response
import io.restassured.specification.RequestSpecification
import javax.websocket.ClientEndpointConfig.Configurator
import javax.websocket._

object TestUtils {

  def authenticateFred(credentials:Credentials): Option[User]= {
    credentials match {
      case Credentials("fred", "password") => Some(User.of("fred", Role.role("TitanOTC:admin"), Role.role("TitanOTC:user")))
      case Credentials("wilma", "password") => Some(User.of("wilma", Role.role("TitanOTC:user")))
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

  def webSocket[T](uri:URI, consumer: Consumer[String]):WebSocketEndpoint[String] = TextWebSocketEndpoint(this, uri, consumer).start()
}

case class TextWebSocketEndpoint(tokens:Tokens, uri:URI, consumer: Consumer[String]) extends WebSocketEndpoint[String](tokens = tokens, uri = uri, consumer = consumer) with MessageHandler.Partial[String] {

  val buffer = new StringWriter

  override def appendBuffer(t: String): Unit = {
    buffer.append(t)
  }

  override def emptyBuffer: String = try {
    buffer.toString
  } finally {
    buffer.getBuffer.setLength(0)
  }

  override def wrap(consumer: Consumer[String]): MessageHandler = this

  override def onMessage(t: String, isLast: Boolean): Unit = {
    appendBuffer(t)
    if(isLast) {
      consumer.accept(emptyBuffer)
    }
  }

}

abstract class WebSocketEndpoint[T](tokens:Tokens, uri:URI, consumer: Consumer[T]) extends Endpoint {

  val getConfigurator: Configurator = new Configurator(){

    override def beforeRequest(headers: util.Map[String, util.List[String]]): Unit = {
      headers.put("Authorization", Collections.singletonList("Bearer "+tokens.authorizationToken))
    }

    override def afterResponse(hr: HandshakeResponse): Unit = {}
  }

  def start(): WebSocketEndpoint[T] = {
    val builder = ClientEndpointConfig.Builder.create
    builder.configurator(getConfigurator)
    val container = ContainerProvider.getWebSocketContainer
    container.connectToServer(this, builder.build, uri)
    this
  }

  def wrap(consumer: Consumer[T]): MessageHandler

  def appendBuffer(t: T)

  def emptyBuffer:T

  override def onOpen(session: Session, endpointConfig: EndpointConfig): Unit = {
    session.addMessageHandler(wrap(consumer))
  }
}

