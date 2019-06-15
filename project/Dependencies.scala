import sbt._

object Dependencies {

  object Versions {
    val catsEffect = "2.0.0-M4"
    val fs2        = "1.1.0-M1"
    val circe      = "0.12.0-M3"
    val amqpClient = "5.7.1"
    val logback    = "1.2.3"
    val monix      = "3.0.0-RC2"
    val zio        = "1.0-RC5"

    val kindProjector    = "0.10.3"
    val betterMonadicFor = "0.3.0"

    val scalaTest  = "3.0.8"
    val scalaCheck = "1.14.0"
  }

  object Libraries {
    sealed trait CurrentScalaVersion
    case object Scala213 extends CurrentScalaVersion
    case object Scala212Or211 extends CurrentScalaVersion
    case object UnknownScalaVersion extends CurrentScalaVersion

    def compilingForScala213(scalaVersionStr: String): CurrentScalaVersion =
      CrossVersion.partialVersion(scalaVersionStr) match {
        case Some((2, 13)) => Scala213
        case Some((2, 12)) => Scala212Or211
        case Some((2, 11)) => Scala212Or211
        case _ => UnknownScalaVersion
      }

    def circe(artifact: String): ModuleID = "io.circe" %% artifact % Versions.circe
    def zio(artifact: String): ModuleID = "org.scalaz" %% artifact % Versions.zio

    lazy val amqpClient = "com.rabbitmq"  % "amqp-client"  % Versions.amqpClient
    lazy val catsEffect = "org.typelevel" %% "cats-effect" % Versions.catsEffect
    lazy val fs2Core    = "co.fs2"        %% "fs2-core"    % Versions.fs2

    // Compiler
    lazy val kindProjector    = "org.typelevel" % "kind-projector"      % Versions.kindProjector cross CrossVersion.binary
    lazy val betterMonadicFor = "com.olegpy"    %% "better-monadic-for" % Versions.betterMonadicFor

    // Examples
    def monix(scalaVersionStr: String): Option[ModuleID] = compilingForScala213(scalaVersionStr) match {
      case Scala212Or211 => Some("io.monix" %% "monix" % Versions.monix)
      case Scala213 => None
      case UnknownScalaVersion => None
    }
    lazy val logback = "ch.qos.logback" % "logback-classic" % Versions.logback

    def zioCore(scalaVersionStr: String): Option[ModuleID] = compilingForScala213(scalaVersionStr) match {
      case Scala212Or211 => Some(zio("scalaz-zio"))
      case Scala213 => None
      case UnknownScalaVersion => None
    }

    def zioCats(scalaVersionStr: String): Option[ModuleID] = compilingForScala213(scalaVersionStr) match {
      case Scala212Or211 => Some(zio("scalaz-zio-interop-cats"))
      case Scala213 => None
      case UnknownScalaVersion => None
    }

    // Json libraries
    lazy val circeCore    = circe("circe-core")
    lazy val circeGeneric = circe("circe-generic")
    lazy val circeParser  = circe("circe-parser")

    // Scala test libraries
    lazy val scalaTest  = "org.scalatest"  %% "scalatest"  % Versions.scalaTest
    lazy val scalaCheck = "org.scalacheck" %% "scalacheck" % Versions.scalaCheck
  }

}
