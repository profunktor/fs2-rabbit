import com.scalapenos.sbt.prompt.SbtPrompt.autoImport._
import com.scalapenos.sbt.prompt._
import Dependencies._
import microsites.ExtraMdFileConfig

name := """fs2-rabbit-root"""

organization in ThisBuild := "com.github.gvolpe"

version in ThisBuild := "0.3"

crossScalaVersions in ThisBuild := Seq("2.11.12", "2.12.4")

sonatypeProfileName := "com.github.gvolpe"

promptTheme := PromptTheme(List(
  text("[SBT] ", fg(136)),
  text(_ => "fs2-rabbit", fg(64)).padRight(" λ ")
 ))

val commonSettings = Seq(
  organizationName := "Fs2 Rabbit",
  startYear := Some(2017),
  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  homepage := Some(url("https://github.com/gvolpe/fs2-rabbit")),
  addCompilerPlugin("org.spire-math" % "kind-projector" % "0.9.5" cross CrossVersion.binary),
  libraryDependencies ++= Seq(
    Libraries.amqpClient,
    Libraries.catsEffect,
    Libraries.fs2Core,
    Libraries.typesafeConfig,
    Libraries.circeCore,
    Libraries.circeGeneric,
    Libraries.circeParser,
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
  scalafmtOnCompile := true,
  coverageExcludedPackages := "com\\.github\\.gvolpe\\.fs2rabbit\\.examples.*;com\\.github\\.gvolpe\\.fs2rabbit\\.typeclasses.*;com\\.github\\.gvolpe\\.fs2rabbit\\.instances.*;.*QueueName*;.*RoutingKey*;.*ExchangeName*;.*DeliveryTag*;.*AmqpClientStream*;.*ConnectionStream*;",
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

lazy val `fs2-rabbit-root` = project.in(file("."))
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

lazy val microsite = project.in(file("site"))
  .enablePlugins(MicrositesPlugin)
  .settings(commonSettings: _*)
  .settings(
    micrositeName := "Fs2 Rabbit",
    micrositeDescription := "Stream-based client for RabbitMQ built on top of Fs2",
    micrositeAuthor := "Gabriel Volpe",
    micrositeGithubOwner := "gvolpe",
    micrositeGithubRepo := "fs2-rabbit",
    micrositeBaseUrl := "/fs2-rabbit",
    micrositeExtraMdFiles := Map(
      file("README.md") -> ExtraMdFileConfig(
        "index.md",
        "home",
        Map("title" -> "Home", "section" -> "home", "position" -> "0")
      )
    ),
    micrositeGitterChannel := false,
    micrositePushSiteWith := GitHub4s,
    micrositeGithubToken := sys.env.get("GITHUB_TOKEN")
  )
  .dependsOn(`fs2-rabbit`)
