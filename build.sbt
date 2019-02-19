name := "akka-http-for-dummies"

version := "0.1"

scalaVersion := "2.12.6"


//scala deps
val scalactic = "org.scalactic" %% "scalactic" % "3.0.5"
val scalatest = "org.scalatest" %% "scalatest" % "3.0.5" % "test"
val akkaHttp = "com.typesafe.akka" %% "akka-http"   % "latest.release"
val akkaStream = "com.typesafe.akka" %% "akka-stream" % "latest.release"
val akkaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "latest.release"
val playJson = "com.typesafe.play" %% "play-json" % "latest.release"
val akkaHttpSprayJson = "com.typesafe.akka" %% "akka-http-spray-json" % "latest.release"
val jwtAkkaHttp = "com.emarsys" %% "jwt-akka-http" % "latest.release"
val restAssurredScalaSupport = "io.rest-assured" % "scala-support" % "latest.release"

val commonScalaDeps = Seq(scalactic, scalatest, akkaHttpSprayJson, akkaLogging)

// java deps
val testng = "org.testng" % "testng" % "latest.release" % "test"
val slf4j = "org.slf4j" % "slf4j-api" % "latest.release"
val logback = "ch.qos.logback" % "logback-classic" % "latest.release"
val restAssurred = "io.rest-assured" % "rest-assured" % "latest.release"
val hamcrest = "org.hamcrest" % "hamcrest-all" % "latest.release"
// val vertxJwt= "io.vertx" % "vertx-auth-jwt" % "latest.release"
val jjwt_api = "io.jsonwebtoken" % "jjwt-api" % "latest.release" % "compile"
val jjwt_impl = "io.jsonwebtoken" % "jjwt-impl" % "latest.release" % "runtime"
val jjwt_jackson = "io.jsonwebtoken" % "jjwt-jackson" % "latest.release" % "runtime"
//val jackson_databind = "com.fasterxml.jackson.core" % "jackson-databind" % "latest.release"



val commonJavaDeps = Seq(slf4j, logback)

lazy val jwt = (project in file("jwt"))
  .settings(
    libraryDependencies ++= commonJavaDeps,
    libraryDependencies ++= Seq(
      jjwt_api,
      jjwt_impl,
      jjwt_jackson,
      testng
    )
  )

lazy val rest = (project in file("rest"))
  .settings(
    libraryDependencies ++= commonJavaDeps,
    libraryDependencies ++= commonScalaDeps,
    libraryDependencies ++= Seq(
      akkaHttp,
      akkaStream,
      restAssurred,
      hamcrest
    )
  ).dependsOn(jwt)

lazy val root = (project in file("."))
  .aggregate(rest, jwt)

