import Dependencies.*
import microsites.ExtraMdFileConfig
import scala.collection.immutable

ThisBuild / name               := "fs2-rabbit"
ThisBuild / scalaVersion       := "2.13.16"
ThisBuild / crossScalaVersions := List("2.13.16", "3.3.4", "3.6.3")
ThisBuild / versionScheme      := Some("semver-spec")
ThisBuild / organization       := "dev.profunktor"
ThisBuild / homepage           := Some(url("https://fs2-rabbit.profunktor.dev/"))
ThisBuild / licenses           := List(License.Apache2)
ThisBuild / developers         := List(
  Developer(
    "gvolpe",
    "Gabriel Volpe",
    "volpegabriel@gmail.com",
    url("https://gvolpe.github.io")
  )
)

publish / skip := true

def scalaOptions(v: String): immutable.Seq[String] =
  CrossVersion.partialVersion(v) match {
    case Some((2, 13)) => List.empty[String]
    case Some((3, _))  => List("-source:3.0-migration")
    case _             => List("-Xmax-classfile-name", "100")
  }

def commonDependencies(v: String): Seq[ModuleID] =
  List(
    Libraries.amqpClient,
    Libraries.catsEffect,
    Libraries.fs2Core,
    Libraries.scalaTest               % Test,
    Libraries.scalaCheck              % Test,
    Libraries.scalaTestPlusScalaCheck % Test
  ) ++
    (CrossVersion.partialVersion(v) match {
      case Some((3, _)) => List.empty
      case _            =>
        List(
          compilerPlugin(Libraries.kindProjector)
        )
    })

val commonSettings = List(
  organizationName      := "ProfunKtor",
  startYear             := Some(2017),
  licenses += License.Apache2,
  homepage              := Some(url("https://fs2-rabbit.profunktor.dev/")),
  headerLicense         := Some(HeaderLicense.ALv2(s"${startYear.value.get}-${java.time.Year.now}", "ProfunKtor")),
  Compile / doc / scalacOptions ++= List("-no-link-warnings"),
  scalacOptions ++= scalaOptions(scalaVersion.value),
  scalacOptions --= List("-Wunused:params", "-Xfatal-warnings"),
  libraryDependencies ++= commonDependencies(scalaVersion.value),
  resolvers += "Apache public" at "https://repository.apache.org/content/groups/public/",
  scalafmtOnCompile     := true,
  mimaPreviousArtifacts := previousStableVersion.value.map(organization.value %% moduleName.value % _).toSet
)

def CoreDependencies(scalaVersionStr: String): List[ModuleID] =
  List(
    Libraries.scodecCats,
    Libraries.logback % Test
  )

def JsonDependencies(scalaVersionStr: String): List[ModuleID] =
  List(
    Libraries.circeCore,
    Libraries.circeGeneric,
    Libraries.circeParser
  )

def ExamplesDependencies(scalaVersionStr: String): List[ModuleID] =
  List(
    Libraries.logback % "runtime",
    Libraries.zioCore,
    Libraries.zioCats,
    Libraries.dropwizard,
    Libraries.dropwizardJmx
  )

def TestKitDependencies(scalaVersionStr: String): List[ModuleID] = List(Libraries.scalaCheck)

def TestsDependencies(scalaVersionStr: String): List[ModuleID] =
  List(
    Libraries.disciplineScalaCheck % Test,
    Libraries.catsLaws             % Test,
    Libraries.catsKernelLaws       % Test
  )

lazy val noPublish = List(
  publish            := {},
  publishLocal       := {},
  publishArtifact    := false,
  publish / skip     := true,
  crossScalaVersions := Nil
)

lazy val `fs2-rabbit-root`: Project = project
  .in(file("."))
  .disablePlugins(MimaPlugin)
  .aggregate(`fs2-rabbit`, `fs2-rabbit-circe`, tests, examples, microsite, `fs2-rabbit-testkit`)
  .settings(noPublish)

lazy val `fs2-rabbit`: Project = project
  .in(file("core"))
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= CoreDependencies(scalaVersion.value))
  .settings(Test / parallelExecution := false)
  .enablePlugins(AutomateHeaderPlugin)

lazy val `fs2-rabbit-circe`: Project = project
  .in(file("json-circe"))
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= JsonDependencies(scalaVersion.value))
  .settings(Test / parallelExecution := false)
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(`fs2-rabbit`)

lazy val tests: Project = project
  .in(file("tests"))
  .settings(commonSettings: _*)
  .settings(noPublish)
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(MimaPlugin)
  .settings(libraryDependencies ++= TestsDependencies(scalaVersion.value))
  .settings(Test / parallelExecution := false)
  .dependsOn(`fs2-rabbit`, `fs2-rabbit-testkit`)

lazy val examples: Project = project
  .in(file("examples"))
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= ExamplesDependencies(scalaVersion.value))
  .settings(noPublish)
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(MimaPlugin)
  .dependsOn(`fs2-rabbit`, `fs2-rabbit-circe`)

lazy val `fs2-rabbit-testkit`: Project = project
  .in(file("testkit"))
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= TestKitDependencies(scalaVersion.value))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(`fs2-rabbit`)

lazy val microsite: Project = project
  .in(file("site"))
  .enablePlugins(MicrositesPlugin)
  .disablePlugins(MimaPlugin)
  .settings(commonSettings: _*)
  .settings(noPublish)
  .settings(
    micrositeName               := "Fs2 Rabbit",
    micrositeDescription        := "RabbitMQ stream-based client",
    micrositeAuthor             := "ProfunKtor",
    micrositeGithubOwner        := "profunktor",
    micrositeGithubRepo         := "fs2-rabbit",
    micrositeBaseUrl            := "",
    micrositeExtraMdFiles       := Map(
      file("README.md")          -> ExtraMdFileConfig(
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
    micrositeExtraMdFilesOutput := (Compile / resourceManaged).value / "jekyll",
    micrositeGitterChannel      := true,
    micrositeGitterChannelUrl   := "profunktor-dev/fs2-rabbit",
    micrositePushSiteWith       := GitHub4s,
    micrositeGithubToken        := sys.env.get("GITHUB_TOKEN"),
    scalacOptions --= List(
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
