import com.scalapenos.sbt.prompt.SbtPrompt.autoImport._
import com.scalapenos.sbt.prompt._
import Dependencies._
import microsites.ExtraMdFileConfig

name := """fs2-rabbit-root"""

organization in ThisBuild := "dev.profunktor"

crossScalaVersions in ThisBuild := Seq("2.12.10", "2.13.1")

sonatypeProfileName := "dev.profunktor"

promptTheme := PromptTheme(
  List(
    text("[sbt] ", fg(105)),
    text(_ => "fs2-rabbit", fg(15)).padRight(" λ ")
  )
)

def maxClassFileName(v: String) = CrossVersion.partialVersion(v) match {
  case Some((2, 13)) => Seq.empty[String]
  case _             => Seq("-Xmax-classfile-name", "100")
}

val commonSettings = Seq(
  organizationName := "ProfunKtor",
  startYear := Some(2017),
  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  homepage := Some(url("https://fs2-rabbit.profunktor.dev/")),
  headerLicense := Some(HeaderLicense.ALv2("2017-2020", "ProfunKtor")),
  scalacOptions in (Compile, doc) ++= Seq("-no-link-warnings"),
  scalacOptions ++= maxClassFileName(scalaVersion.value),
  libraryDependencies ++= {
    Seq(
      compilerPlugin(Libraries.kindProjector),
      compilerPlugin(Libraries.betterMonadicFor),
      Libraries.amqpClient,
      Libraries.catsEffect,
      Libraries.fs2Core,
      Libraries.scalaTest  % Test,
      Libraries.scalaCheck % Test
    )
  },
  resolvers += "Apache public" at "https://repository.apache.org/content/groups/public/",
  scalafmtOnCompile := true,
  publishTo := {
    val sonatype = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at sonatype + "content/repositories/snapshots")
    else
      Some("releases" at sonatype + "service/local/staging/deploy/maven2")
  },
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ =>
    false
  },
  pomExtra :=
    <developers>
        <developer>
          <id>gvolpe</id>
          <name>Gabriel Volpe</name>
          <url>https://github.com/gvolpe</url>
        </developer>
      </developers>
)

def CoreDependencies(scalaVersionStr: String): Seq[ModuleID] = Seq(Libraries.logback % Test)

def JsonDependencies(scalaVersionStr: String): Seq[ModuleID] =
  Seq(
    Libraries.circeCore,
    Libraries.circeGeneric,
    Libraries.circeParser
  )

def ExamplesDependencies(scalaVersionStr: String): Seq[ModuleID] =
  Seq(
    Libraries.logback % "runtime",
    Libraries.monix,
    Libraries.zioCore,
    Libraries.zioCats
  )

lazy val noPublish = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false,
  skip in publish := true
)

lazy val `fs2-rabbit-root` = project
  .in(file("."))
  .aggregate(`fs2-rabbit`, `fs2-rabbit-circe`, tests, examples, microsite)
  .settings(noPublish)

lazy val `fs2-rabbit` = project
  .in(file("core"))
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= CoreDependencies(scalaVersion.value))
  .settings(parallelExecution in Test := false)
  .enablePlugins(AutomateHeaderPlugin)

lazy val `fs2-rabbit-circe` = project
  .in(file("json-circe"))
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= JsonDependencies(scalaVersion.value))
  .settings(parallelExecution in Test := false)
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(`fs2-rabbit`)

lazy val tests = project
  .in(file("tests"))
  .settings(commonSettings: _*)
  .settings(noPublish)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(parallelExecution in Test := false)
  .dependsOn(`fs2-rabbit`)

lazy val examples = project
  .in(file("examples"))
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= ExamplesDependencies(scalaVersion.value))
  .settings(noPublish)
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(`fs2-rabbit`, `fs2-rabbit-circe`)

lazy val microsite = project
  .in(file("site"))
  .enablePlugins(MicrositesPlugin)
  .settings(commonSettings: _*)
  .settings(noPublish)
  .settings(
    micrositeName := "Fs2 Rabbit",
    micrositeDescription := "RabbitMQ stream-based client",
    micrositeAuthor := "ProfunKtor",
    micrositeGithubOwner := "profunktor",
    micrositeGithubRepo := "fs2-rabbit",
    micrositeBaseUrl := "",
    micrositeExtraMdFiles := Map(
      file("README.md") -> ExtraMdFileConfig(
        "index.md",
        "home",
        Map("title" -> "Home", "position" -> "0")
      ),
      file("CODE_OF_CONDUCT.md") -> ExtraMdFileConfig(
        "CODE_OF_CONDUCT.md",
        "page",
        Map("title" -> "Code of Conduct")
      )
    ),
    micrositeExtraMdFilesOutput := (resourceManaged in Compile).value / "jekyll",
    micrositeGitterChannel := true,
    micrositeGitterChannelUrl := "profunktor-dev/fs2-rabbit",
    micrositePushSiteWith := GitHub4s,
    micrositeGithubToken := sys.env.get("GITHUB_TOKEN"),
    scalacOptions --= Seq(
          "-Werror",
          "-Xfatal-warnings",
          "-Ywarn-unused-import",
          "-Ywarn-numeric-widen",
          "-Ywarn-dead-code",
          "-Xlint:-missing-interpolator,_"
        )
  )
  .dependsOn(`fs2-rabbit`, `fs2-rabbit-circe`, `examples`)

// CI build
addCommandAlias("buildFs2Rabbit", ";clean;+test;mdoc")
