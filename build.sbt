import com.scalapenos.sbt.prompt.SbtPrompt.autoImport._
import com.scalapenos.sbt.prompt._
import Dependencies._
import microsites.ExtraMdFileConfig

name := """fs2-rabbit-root"""

organization in ThisBuild := "com.github.gvolpe"

crossScalaVersions in ThisBuild := Seq("2.11.12", "2.12.8")

sonatypeProfileName := "com.github.gvolpe"

promptTheme := PromptTheme(List(
  text("[SBT] ", fg(136)),
  text(_ => "fs2-rabbit", fg(64)).padRight(" λ ")
 ))

lazy val commonScalacOptions = Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:experimental.macros",
  "-Ypartial-unification",
  "-unchecked",
  "-Xfatal-warnings",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-value-discard",
  "-Xfuture",
  "-Xlog-reflective-calls",
  "-Ywarn-inaccessible",
  "-Ypatmat-exhaust-depth",
  "20",
  "-Ydelambdafy:method",
  "-Xmax-classfile-name",
  "100"
)


lazy val warnUnusedImport = Seq(
  scalacOptions += "-Ywarn-unused-import",
  scalacOptions in (Compile, console) ~= { _.filterNot(Seq("-Xlint", "-Ywarn-unused-import").contains) },
  scalacOptions in (Test, console) := (scalacOptions in (Compile, console)).value
)

val commonSettings = Seq(
  organizationName := "Fs2 Rabbit",
  startYear := Some(2017),
  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  homepage := Some(url("https://gvolpe.github.io/fs2-rabbit/")),
  headerLicense := Some(HeaderLicense.ALv2("2017-2019", "Fs2 Rabbit")),
  libraryDependencies ++= Seq(
    compilerPlugin(Libraries.kindProjector),
    compilerPlugin(Libraries.betterMonadicFor),
    Libraries.amqpClient,
    Libraries.catsEffect,
    Libraries.fs2Core,
    Libraries.scalaTest % "test",
    Libraries.scalaCheck % "test"
  ),
  resolvers += "Apache public" at "https://repository.apache.org/content/groups/public/",
  scalacOptions ++= commonScalacOptions,
  scalafmtOnCompile := true,
  coverageExcludedPackages :=
  "com\\.github\\.gvolpe\\.fs2rabbit\\.examples.*;com\\.github\\.gvolpe\\.fs2rabbit\\.effects.*;.*QueueName*;.*RoutingKey*;.*ExchangeName*;.*DeliveryTag*;.*AMQPClientStream*;.*ConnectionStream*;",
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
      <developers>
        <developer>
          <id>gvolpe</id>
          <name>Gabriel Volpe</name>
          <url>http://github.com/gvolpe</url>
        </developer>
      </developers>
) ++ warnUnusedImport

val CoreDependencies: Seq[ModuleID] = Seq(
  Libraries.logback % "test"
)

val JsonDependencies: Seq[ModuleID] = Seq(
  Libraries.circeCore,
  Libraries.circeGeneric,
  Libraries.circeParser
)

val ExamplesDependencies: Seq[ModuleID] = Seq(
  Libraries.monix,
  Libraries.zioCore,
  Libraries.zioCats,
  Libraries.logback % "runtime"
)

lazy val noPublish = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false,
  skip in publish := true
)

lazy val `fs2-rabbit-root` = project.in(file("."))
  .aggregate(`fs2-rabbit`, `fs2-rabbit-circe`, `fs2-rabbit-test-support`, tests, examples, microsite)
  .settings(noPublish)

lazy val `fs2-rabbit` = project.in(file("core"))
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= CoreDependencies)
  .settings(parallelExecution in Test := false)
  .enablePlugins(AutomateHeaderPlugin)

lazy val `fs2-rabbit-circe` = project.in(file("json-circe"))
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= JsonDependencies)
  .settings(parallelExecution in Test := false)
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(`fs2-rabbit`)

lazy val `fs2-rabbit-test-support` = project.in(file("test-support"))
  .settings(commonSettings: _*)
  .settings(libraryDependencies += Libraries.scalaTest)
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(`fs2-rabbit`)

lazy val tests = project.in(file("tests"))
  .settings(commonSettings: _*)
  .settings(noPublish)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(parallelExecution in Test := false)
  .dependsOn(`fs2-rabbit-test-support` % Test)

lazy val examples = project.in(file("examples"))
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= ExamplesDependencies)
  .settings(noPublish)
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(`fs2-rabbit`, `fs2-rabbit-circe`)

lazy val microsite = project.in(file("site"))
  .enablePlugins(MicrositesPlugin)
  .settings(commonSettings: _*)
  .settings(noPublish)
  .settings(
    micrositeName := "Fs2 Rabbit",
    micrositeDescription := "RabbitMQ stream-based client",
    micrositeAuthor := "Gabriel Volpe",
    micrositeGithubOwner := "gvolpe",
    micrositeGithubRepo := "fs2-rabbit",
    micrositeBaseUrl := "/fs2-rabbit",
    micrositeExtraMdFiles := Map(
      file("README.md") -> ExtraMdFileConfig(
        "index.md",
        "home",
        Map("title" -> "Home", "position" -> "0")
      )
    ),
    micrositePalette := Map(
      "brand-primary"     -> "#E05236",
      "brand-secondary"   -> "#774615",
      "brand-tertiary"    -> "#2f2623",
      "gray-dark"         -> "#453E46",
      "gray"              -> "#837F84",
      "gray-light"        -> "#E3E2E3",
      "gray-lighter"      -> "#F4F3F4",
      "white-color"       -> "#FFFFFF"
    ),
    micrositeGitterChannel := true,
    micrositeGitterChannelUrl := "fs2-rabbit/fs2-rabbit",
    micrositePushSiteWith := GitHub4s,
    micrositeGithubToken := sys.env.get("GITHUB_TOKEN"),
    fork in tut := true,
    scalacOptions in Tut --= Seq(
      "-Xfatal-warnings",
      "-Ywarn-unused-import",
      "-Ywarn-numeric-widen",
      "-Ywarn-dead-code",
      "-Xlint:-missing-interpolator,_",
    )
  )
  .dependsOn(`fs2-rabbit`, `fs2-rabbit-circe`, `examples`)

// CI build
addCommandAlias("buildFs2Rabbit", ";clean;+coverage;+test;+coverageReport;+coverageAggregate;tut")

