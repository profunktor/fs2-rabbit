import sbt._

object Dependencies {

  object Versions {
    val catsEffect = "2.0.0-M4"
    val fs2        = "1.1.0-M1"
    val circe      = "0.12.0-M3"
    val amqpClient = "5.7.1"
    val logback    = "1.2.3"
    //val monix      = "3.0.0-RC2"
    //val zio        = "1.0-RC5"

    val kindProjector    = "0.10.3"
    val betterMonadicFor = "0.3.0"

    val scalaTest  = "3.0.8"
    val scalaCheck = "1.14.0"
  }

  object Libraries {
    def circe(artifact: String): ModuleID = "io.circe" %% artifact % Versions.circe
    //def zio(artifact: String): ModuleID = "org.scalaz" %% artifact % Versions.zio

    lazy val amqpClient = "com.rabbitmq"  % "amqp-client"  % Versions.amqpClient
    lazy val catsEffect = "org.typelevel" %% "cats-effect" % Versions.catsEffect
    lazy val fs2Core    = "co.fs2"        %% "fs2-core"    % Versions.fs2

    // Compiler
    lazy val kindProjector    = "org.typelevel" % "kind-projector"      % Versions.kindProjector cross CrossVersion.binary
    lazy val betterMonadicFor = "com.olegpy"    %% "better-monadic-for" % Versions.betterMonadicFor

    // Examples
    //lazy val monix   = "io.monix"       %% "monix"          % Versions.monix
    lazy val logback = "ch.qos.logback" % "logback-classic" % Versions.logback

    //lazy val zioCore = zio("scalaz-zio")
    //lazy val zioCats = zio("scalaz-zio-interop-cats")

    // Json libraries
    lazy val circeCore    = circe("circe-core")
    lazy val circeGeneric = circe("circe-generic")
    lazy val circeParser  = circe("circe-parser")

    // Scala test libraries
    lazy val scalaTest  = "org.scalatest"  %% "scalatest"  % Versions.scalaTest
    lazy val scalaCheck = "org.scalacheck" %% "scalacheck" % Versions.scalaCheck
  }

}
