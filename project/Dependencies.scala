import sbt._

object Dependencies {

  object Versions {
    val fs2             = "0.10.0-RC2"
    val circe           = "0.9.1"
    val qpidBroker      = "6.1.2"
    val amqpClient      = "4.1.0"
    val typesafeConfig  = "1.3.1"
    val logback         = "1.1.3"
    val monix           = "3.0.0-M3"

    val scalaTest       = "3.0.1"
    val scalaCheck      = "1.13.4"
  }

  object Libraries {
    lazy val amqpClient     = "com.rabbitmq"    %  "amqp-client"      % Versions.amqpClient
    lazy val fs2Core        = "co.fs2"          %% "fs2-core"         % Versions.fs2
    lazy val typesafeConfig = "com.typesafe"    % "config"            % Versions.typesafeConfig

    // Examples
    lazy val monix          = "io.monix"        %% "monix"            % Versions.monix
    lazy val logback        = "ch.qos.logback"  % "logback-classic"   % Versions.logback

    // Json libraries
    lazy val circeCore      = "io.circe" %% "circe-core"    % Versions.circe
    lazy val circeGeneric   = "io.circe" %% "circe-generic" % Versions.circe
    lazy val circeParser    = "io.circe" %% "circe-parser"  % Versions.circe

    // Qpid Broker libraries for test
    lazy val qpidBrokerCore   = "org.apache.qpid"           % "qpid-broker-core"                      % Versions.qpidBroker % "test"
    lazy val qpidMemoryStore  = "org.apache.qpid"           % "qpid-broker-plugins-memory-store"      % Versions.qpidBroker % "test"
    lazy val qpidAmqpProtocol = "org.apache.qpid"           % "qpid-broker-plugins-amqp-0-8-protocol" % Versions.qpidBroker % "test"
    lazy val qpidClient       = "org.apache.qpid"           % "qpid-client"                           % Versions.qpidBroker % "test"
    lazy val geronimoJmsSpec  = "org.apache.geronimo.specs" % "geronimo-jms_1.1_spec"                 % "1.1.1"           % "test"

    // Scala test libraries
    lazy val scalaTest  = "org.scalatest"   %% "scalatest"  % Versions.scalaTest  % "test"
    lazy val scalaCheck = "org.scalacheck"  %% "scalacheck" % Versions.scalaCheck % "test"
  }

}
