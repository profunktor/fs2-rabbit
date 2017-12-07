name := """fs2-rabbit-root"""

organization in ThisBuild := "com.github.gvolpe"

version in ThisBuild := "0.0.19-SNAPSHOT"

crossScalaVersions in ThisBuild := Seq("2.11.11", "2.12.3")

val circeVersion = "0.9.0-M2"
val qpidBrokerVersion = "6.1.2"

val commonSettings = Seq(
  licenses +=("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
  homepage := Some(url("https://github.com/gvolpe/fs2-rabbit")),
  libraryDependencies ++= Seq(
    "com.rabbitmq"    %  "amqp-client"      % "4.1.0",
    "co.fs2"          %% "fs2-core"         % "0.10.0-M9",
    "org.typelevel"   %% "cats-effect"      % "0.5",
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
    "org.scalatest"   %% "scalatest"  % "3.0.1" % "test",
    "org.scalacheck"  %% "scalacheck" % "1.13.4" % "test"
  ),
  resolvers += "Apache public" at "https://repository.apache.org/content/groups/public/",
  scalacOptions ++= Seq(
    "-Xmax-classfile-name", "80",
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-Ypartial-unification",
    "-language:existentials",
    "-language:higherKinds"
  ),
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
  "io.monix"        %% "monix"              % "3.0.0-M2",
  "ch.qos.logback"  %  "logback-classic"    % "1.1.3" % "runtime"
)

lazy val noPublish = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

lazy val root = project.in(file("."))
  .aggregate(`fs2-rabbit`, `fs2-rabbit-examples`)
  .settings(noPublish)

lazy val `fs2-rabbit` = project.in(file("core"))
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= CoreDependencies)
  .settings(parallelExecution in Test := false) // qpid fails on tests run in parallel

lazy val `fs2-rabbit-examples` = project.in(file("examples"))
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= ExamplesDependencies)
  .settings(noPublish)
  .dependsOn(`fs2-rabbit`)

sonatypeProfileName := "com.github.gvolpe"

//resolvers += Resolver.sonatypeRepo("releases")
//addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
