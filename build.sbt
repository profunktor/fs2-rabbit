name := """fs2-rabbit"""

version := "1.0"

scalaVersion := "2.11.8"

lazy val circeVersion = "0.5.1"

libraryDependencies ++= Seq(
  "com.rabbitmq"  %  "amqp-client"    % "4.1.0",
  "co.fs2"        %% "fs2-core"       % "0.9.6",
  "io.circe"      %% "circe-core"     % circeVersion,
  "io.circe"      %% "circe-generic"  % circeVersion,
  "io.circe"      %% "circe-parser"   % circeVersion,
  "org.scalatest" %% "scalatest"      % "2.2.4" % "test"
)

