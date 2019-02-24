package dummies

import java.security.KeyFactory
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}
import java.time.ZonedDateTime
import java.util.Base64

import com.github.dfauth.jwt_jaas.jwt.Role.role
import com.github.dfauth.jwt_jaas.jwt.{JWTBuilder, JWTVerifier, User}
import com.typesafe.scalalogging.LazyLogging

object Main extends LazyLogging {

  def main(args: Array[String]): Unit = {
    val component = Component("say hello to %s from a component")

    val factory = KeyFactory.getInstance("RSA")
    val publicKeySpec = new X509EncodedKeySpec(Base64.getMimeDecoder.decode(PUBLIC_KEY))
    val privateKeySpec = new PKCS8EncodedKeySpec(Base64.getMimeDecoder.decode(PRIVATE_KEY))
    val publicKey = KeyFactory.getInstance("RSA").generatePublic(publicKeySpec)
    val privateKey = KeyFactory.getInstance("RSA").generatePrivate(privateKeySpec)
    val jwtVerifier = new JWTVerifier(publicKey)
    val jwtBuilder = new JWTBuilder("me",privateKey)
    val user = User.of("fred", role("test:admin"), role("test:user"))
    val token = jwtBuilder.forSubject(user.getUserId).withClaim("roles", user.getRoles).withExpiry(ZonedDateTime.now().plusSeconds(20)).build()
    println(s"token is ${token}")

    import Routes._
    val endPoint = RestEndPointServer(hello(jwtVerifier))
    val bindingFuture = endPoint.start()

    scala.io.StdIn.readLine()

    endPoint.stop(bindingFuture)
  }


  val PUBLIC_KEY:String = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxp2dKx4OB2SrcLCQI2IjeoC0jwUSy8IJHjaVy4iyf8AOticPkI/up0Giu/OVqhbfYxxDehAWiWjdK8/ipQg2z2K1nawEn9tae21uQvwr+0d1mkN0UZpHex+TK4aGwOBkWTnoSH0ck1wLdPS3Au6RzmAIvjAPZNQiKbpIioLNlhF0Fv77GzpeRsKXB75AqHaWbPbvfFF4vYfNK1NqSVTh+TLI9bQLwk1irxEB/tryzEvTLcEb6xK8OBT6GBWP9WL6tPCiyMHNv3iwugFWW2tntmCImrpgZE0I/9Ha5QgQeQ9xM4CaqkGjIg3ozuBknpnTjWzeYlI5IK142V+x48LbvwIDAQAB"
  val PRIVATE_KEY:String = "MIICOAIBADANBgkqhkiG9w0BAQEFAASCAiIwggIeAgEAAoIBAQDGnZ0rHg4HZKtwsJAjYiN6gLSPBRLLwgkeNpXLiLJ/wA62Jw+Qj+6nQaK785WqFt9jHEN6EBaJaN0rz+KlCDbPYrWdrASf21p7bW5C/Cv7R3WaQ3RRmkd7H5MrhobA4GRZOehIfRyTXAt09LcC7pHOYAi+MA9k1CIpukiKgs2WEXQW/vsbOl5GwpcHvkCodpZs9u98UXi9h80rU2pJVOH5Msj1tAvCTWKvEQH+2vLMS9MtwRvrErw4FPoYFY/1Yvq08KLIwc2/eLC6AVZba2e2YIiaumBkTQj/0drlCBB5D3EzgJqqQaMiDejO4GSemdONbN5iUjkgrXjZX7Hjwtu/AgEAAoIBADbJd3dTXQ9RB7GYIsp/4cWDB0uEXMD0D0vURtHULVjsA6Lfd32rFmvwwRETii9XC9vtCff7xBu3X3scZyqa73OZiPurXcMy4Oy3LPkxUniIJ7qb7NQtuJYQCaqx+y3tOAEc5iRIzr8fXtGuR2V5paLF/uNnondvxNS53BTJLCi+vtIi7yTHHlzl5E3/PWYPqj4qxcL1OzV8GJnPjG9Sn+UvMyWtgtp0Y6eapoa3dcgVS/vsj3S+hH5styyKGh7/cQELJNeA7wcW6WCBkqxHjdMgTYiidXXzOjWXdeuwehfnYaBqtCWQPnBVulNOTv7Thq2N+XHeqqf92W9BJeXTYoECAQACAQACAQACAQACAQA="
}
