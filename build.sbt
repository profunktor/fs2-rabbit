import Dependencies._

name := """fs2-rabbit-root"""

organization in ThisBuild := "com.github.gvolpe"

version in ThisBuild := "0.1-M3"

crossScalaVersions in ThisBuild := Seq("2.11.12", "2.12.4")

val commonSettings = Seq(
  organizationName := "Fs2 Rabbit",
  startYear := Some(2017),
  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  homepage := Some(url("https://github.com/gvolpe/fs2-rabbit")),
  addCompilerPlugin("org.spire-math" % "kind-projector" % "0.9.5" cross CrossVersion.binary),
  libraryDependencies ++= Seq(
    Libraries.amqpClient,
    Libraries.fs2Core,
    Libraries.typesafeConfig,
    Libraries.circeCore,
    Libraries.circeGeneric,
    Libraries.circeParser,
    Libraries.qpidBrokerCore,
    Libraries.qpidMemoryStore,
    Libraries.qpidAmqpProtocol,
    Libraries.qpidClient,
    Libraries.geronimoJmsSpec,
    Libraries.scalaTest,
    Libraries.scalaCheck
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
  coverageExcludedPackages := "com\\.github\\.gvolpe\\.fs2rabbit\\.examples.*;.*QueueName*;.*RoutingKey*;.*ExchangeName*;.*DeliveryTag*",
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
  Libraries.logback % "test"
)

val ExamplesDependencies: Seq[ModuleID] = Seq(
  Libraries.monix,
  Libraries.logback % "runtime"
)

lazy val noPublish = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false,
  skip in publish := true
)

lazy val root = project.in(file("."))
  .aggregate(`fs2-rabbit`, `fs2-rabbit-examples`)
  .settings(noPublish)

lazy val `fs2-rabbit` = project.in(file("core"))
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= CoreDependencies)
  .settings(parallelExecution in Test := false)
  .enablePlugins(AutomateHeaderPlugin)

lazy val `fs2-rabbit-examples` = project.in(file("examples"))
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= ExamplesDependencies)
  .settings(noPublish)
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(`fs2-rabbit`)

sonatypeProfileName := "com.github.gvolpe"

//resolvers += Resolver.sonatypeRepo("releases")
//addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
