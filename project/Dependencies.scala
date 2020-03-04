import sbt._

object Dependencies {

  object Version {
    val catsEffect = "2.1.2"
    val fs2        = "2.2.2"
    val circe      = "0.13.0"
    val amqpClient = "5.8.0"
    val logback    = "1.2.3"
    val monix      = "3.1.0"
    val zio        = "1.0.0-RC18-1"
    val zioCats    = "2.0.0.0-RC10"

    val kindProjector    = "0.11.0"
    val betterMonadicFor = "0.3.1"

    val scalaTest  = "3.0.8"
    val scalaCheck = "1.14.3"
  }

  object Libraries {
    def circe(artifact: String): ModuleID = "io.circe" %% artifact % Version.circe

    lazy val amqpClient = "com.rabbitmq"  % "amqp-client"  % Version.amqpClient
    lazy val catsEffect = "org.typelevel" %% "cats-effect" % Version.catsEffect
    lazy val fs2Core    = "co.fs2"        %% "fs2-core"    % Version.fs2

    // Compiler
    lazy val kindProjector    = "org.typelevel" % "kind-projector"      % Version.kindProjector cross CrossVersion.full
    lazy val betterMonadicFor = "com.olegpy"    %% "better-monadic-for" % Version.betterMonadicFor

    // Examples
    lazy val logback = "ch.qos.logback" % "logback-classic"   % Version.logback
    lazy val monix   = "io.monix"       %% "monix"            % Version.monix
    lazy val zioCore = "dev.zio"        %% "zio"              % Version.zio
    lazy val zioCats = "dev.zio"        %% "zio-interop-cats" % Version.zioCats

    // Json libraries
    lazy val circeCore    = circe("circe-core")
    lazy val circeGeneric = circe("circe-generic")
    lazy val circeParser  = circe("circe-parser")

    // Scala test libraries
    lazy val scalaTest  = "org.scalatest"  %% "scalatest"  % Version.scalaTest
    lazy val scalaCheck = "org.scalacheck" %% "scalacheck" % Version.scalaCheck
  }

}
