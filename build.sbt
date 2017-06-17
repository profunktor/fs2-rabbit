name := """fs2-rabbit-root"""

organization in ThisBuild := "com.github.gvolpe"

version in ThisBuild := "0.0.10-SNAPSHOT"

scalaVersion := "2.11.8"

val circeVersion = "0.8.0"
val qpidBrokerVersion = "6.1.2"

val commonSettings = Seq(
  licenses +=("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
  homepage := Some(url("https://github.com/gvolpe/fs2-rabbit")),
  scalaVersion := "2.11.8",
  libraryDependencies ++= Seq(
    "com.rabbitmq"    %  "amqp-client"      % "4.1.0",
    "co.fs2"          %% "fs2-core"         % "0.10.0-M2",
    "org.typelevel"   %% "cats-effect"      % "0.3",
    "com.typesafe"    % "config"            % "1.3.1",

    // Json libraries
    "io.circe" %% "circe-core"    % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser"  % circeVersion,

    // Qpid Broker libraries for test
    "org.apache.qpid"           % "qpid-broker-core"                      % qpidBrokerVersion % "test",
    "org.apache.qpid"           % "qpid-broker-plugins-memory-store"      % qpidBrokerVersion % "test",
    "org.apache.qpid"           % "qpid-broker-plugins-amqp-0-8-protocol" % qpidBrokerVersion % "test",
    "org.apache.qpid"           % "qpid-client"                           % qpidBrokerVersion % "test",
    "org.apache.geronimo.specs" % "geronimo-jms_1.1_spec"                 % "1.1.1"           % "test",

    // Scala test libraries
    "org.scalatest"   %% "scalatest"  % "2.2.4" % "test",
    "org.scalacheck"  %% "scalacheck" % "1.12.5" % "test"
  ),
  resolvers += "Apache public" at "https://repository.apache.org/content/groups/public/",
  scalacOptions ++= Seq(
    "-Xlint"
    // "-Xfatal-warnings",
    // "-feature"
    // "-deprecation", //hard to handle when supporting multiple scala versions...
    // , "-Xlog-implicits"
    //"-Ydebug"
  ),
  incOptions := incOptions.value.withNameHashing(true),
  coverageExcludedPackages := "com\\.github\\.gvolpe\\.fs2rabbit\\.examples.*;.*UnderlyingAmqpClient*",
  publishTo := {
    val sonatype = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at sonatype + "content/repositories/snapshots")
    else
      Some("releases" at sonatype + "service/local/staging/deploy/maven2")
  },
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  pomExtra :=
    <scm>
      <url>git@github.com:gvolpe/fs2-rabbit.git</url>
      <connection>scm:git:git@github.com:gvolpe/fs2-rabbit.git</connection>
    </scm>
      <developers>
        <developer>
          <id>gvolpe</id>
          <name>Gabriel Volpe</name>
          <url>http://github.com/gvolpe</url>
        </developer>
      </developers>
)

val CoreDependencies: Seq[ModuleID] = Seq(
  "ch.qos.logback" % "logback-classic" % "1.1.3" % "test"
)

val ExamplesDependencies: Seq[ModuleID] = Seq(
  "io.monix"        %% "monix"              % "3.0.0-7a337f9",
  //  "org.scalaz"      %% "scalaz-concurrent"  % "7.2.13",
  "ch.qos.logback"  %  "logback-classic"    % "1.1.3" % "runtime"
)

lazy val root = project.in(file("."))
  .aggregate(`fs2-rabbit`, `fs2-rabbit-examples`)
  .settings(publishLocal := {})
  .settings(publish := {})

lazy val `fs2-rabbit` = project.in(file("core"))
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= CoreDependencies)

lazy val `fs2-rabbit-examples` = project.in(file("examples"))
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= ExamplesDependencies)
  .settings(publishLocal := {})
  .settings(publish := {})
  .dependsOn(`fs2-rabbit`)

sonatypeProfileName := "com.github.gvolpe"

publishArtifact := false

//resolvers += Resolver.sonatypeRepo("releases")
//addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
