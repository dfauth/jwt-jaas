name := "jwt-jaas"

version := "0.1"

scalaVersion := "2.12.6"
val kafkaVersion = "2.1.1"

//scala deps
val scalactic = "org.scalactic" %% "scalactic" % "3.0.5"
val scalatest = "org.scalatest" %% "scalatest" % "3.0.5" % "test"
// val scalatest_testng = "org.scalatest" %% "scalatest-testng" % "3.0.0-SNAP13"
val akkaHttp = "com.typesafe.akka" %% "akka-http"   % "latest.release"
val akkaStream = "com.typesafe.akka" %% "akka-stream" % "latest.release"
val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "latest.release"
val playJson = "com.typesafe.play" %% "play-json" % "latest.release"
val akkaHttpSprayJson = "com.typesafe.akka" %% "akka-http-spray-json" % "latest.release"
//val jwtAkkaHttp = "com.emarsys" %% "jwt-akka-http" % "latest.release"
val restAssurredScalaSupport = "io.rest-assured" %% "scala-support" % "latest.release"
val embeddedKafka = "io.github.embeddedkafka" %% "embedded-kafka" % "2.1.1" % Test withSources()
val kafkaCore = "org.apache.kafka" %% "kafka" % kafkaVersion
val embeddedKafkaStreams = "net.manub" %% "scalatest-embedded-kafka-streams" % "2.0.0" % Test
// val kafkaTest = "org.apache.kafka" %% "test" % "latest.release" % "test" //"1.2.2.RELEASE"
val scalaJava8 = "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.0"

val commonScalaDeps = Seq(scalactic, scalatest, akkaHttpSprayJson, scalaLogging)

// java deps
val testng = "org.testng" % "testng" % "6.14.3" % "test"
val slf4j = "org.slf4j" % "slf4j-api" % "latest.release"
val logback = "ch.qos.logback" % "logback-classic" % "latest.release"
val restAssurred = "io.rest-assured" % "rest-assured" % "latest.release"
val hamcrest = "org.hamcrest" % "hamcrest-all" % "latest.release"
// val vertxJwt= "io.vertx" % "vertx-auth-jwt" % "latest.release"
val jjwt_api = "io.jsonwebtoken" % "jjwt-api" % "latest.release" //% "compile"
val jjwt_impl = "io.jsonwebtoken" % "jjwt-impl" % "latest.release" //% "runtime,test"
val jjwt_jackson = "io.jsonwebtoken" % "jjwt-jackson" % "latest.release" //% "runtime"
val javax_ws_rs = "javax.ws.rs" % "javax.ws.rs-api" % "2.1.1" % Test
//val jackson_databind = "com.fasterxml.jackson.core" % "jackson-databind" % "latest.release"

val kafkaStreams = "org.apache.kafka" % "kafka-streams" % kafkaVersion withSources()

val kafkaClient = "org.apache.kafka" % "kafka-clients" % kafkaVersion withSources()

//val springKafka = "org.springframework.kafka" % "spring-kafka" % "2.2.4.RELEASE" //"latest.release" //"1.2.2.RELEASE"
//val springKafkaTest = "org.springframework.kafka" % "spring-kafka-test" % "2.2.4.RELEASE" % Test //"1.2.2.RELEASE"


val commonJavaDeps = Seq(slf4j, logback, testng)

val jwtLibraryDependencies = commonJavaDeps ++ Seq(jjwt_api, jjwt_impl, jjwt_jackson, testng)

lazy val jwt = (project in file("jwt"))
  .enablePlugins(TestNGPlugin)
  .settings(
    testNGVersion := "6.14.3",
    libraryDependencies ++= jwtLibraryDependencies
  )

val restLibraryDependencies = commonJavaDeps ++ commonScalaDeps ++ Seq(akkaHttp,
  akkaStream,
  restAssurred,
  hamcrest)

lazy val rest = (project in file("rest"))
  .settings(
    libraryDependencies ++= restLibraryDependencies
  ).dependsOn(jwt, common % "compile->compile;test->test")

val apiGatewayLibraryDependencies = commonScalaDeps ++ Seq(akkaHttp,
  akkaStream,
  restAssurred,
  hamcrest)

lazy val apiGateway = (project in file("apiGateway"))
  .settings(
    libraryDependencies ++= apiGatewayLibraryDependencies
  ).dependsOn(rest)

val kafkaLibraryDependencies = commonScalaDeps ++ Seq(akkaHttp,
  akkaStream,
  scalaJava8,
  kafkaClient,
  embeddedKafka,
  embeddedKafkaStreams,
  javax_ws_rs,
  logback
)

lazy val kafka = (project in file("kafka"))
  .settings(
    libraryDependencies ++= kafkaLibraryDependencies
  ).dependsOn(common % "compile->compile;test->test", jwt)

val commonLibraryDependencies = commonScalaDeps ++ Seq(akkaHttp,
  akkaStream,
  logback
)

lazy val common = (project in file("common"))
  .settings(
    libraryDependencies ++= commonLibraryDependencies
  )

val authznLibraryDependencies = commonJavaDeps //++ Seq(logback)

lazy val authzn = (project in file("authzn"))
  .enablePlugins(TestNGPlugin)
  .settings(
    testNGVersion := "6.14.3",
    libraryDependencies ++= authznLibraryDependencies
  )

val authznScalaLibraryDependencies = commonJavaDeps ++ commonScalaDeps

lazy val authznScala = (project in file("authzn-scala"))
  .settings(
    libraryDependencies ++= authznScalaLibraryDependencies
  ).dependsOn(authzn % "compile->compile;test->test")

lazy val root = (project in file("."))
  .settings(
    libraryDependencies ++= commonLibraryDependencies
                        ++ restLibraryDependencies
                        ++ jwtLibraryDependencies
                        ++ kafkaLibraryDependencies
  ).dependsOn(rest % "compile->compile;test->test", jwt, kafka % "compile->compile;test->test", common % "compile->compile;test->test", authzn, authznScala, apiGateway)




