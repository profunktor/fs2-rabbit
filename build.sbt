import com.scalapenos.sbt.prompt.SbtPrompt.autoImport._
import com.scalapenos.sbt.prompt._
import Dependencies._
import microsites.ExtraMdFileConfig

name := """fs2-rabbit-root"""

organization in ThisBuild := "dev.profunktor"

crossScalaVersions in ThisBuild := Seq("2.11.12", "2.12.8", "2.13.0")

// makes `tut` fail :( -> https://github.com/tpolecat/tut/issues/255
//scalaVersion in ThisBuild := "2.12.8" // needed for metals

sonatypeProfileName := "dev.profunktor"

promptTheme := PromptTheme(List(
  text("[sbt] ", fg(105)),
  text(_ => "fs2-rabbit", fg(15)).padRight(" λ ")
 ))



// We use String as our input type because `scalaVersion.value` cannot be called
// in a lot of places in a build.sbt file where it would be convenient to do so
// and so we have to thread it through at the last moment instead and
// scalaVersion.value is a String.
def determineVersionSpecificDeps(scalaVersionStr: String) = CrossVersion.partialVersion(scalaVersionStr) match {
  case Some((2, 13)) => Scala213Dependencies
  case Some((2, 12)) => Scala212Dependencies
  case Some((2, 11)) => Scala211Dependencies
  // Fallback to 2.12 libraries as they're currently the most well-supported
  case _ => Scala212Dependencies
}

val commonSettings = Seq(
  organizationName := "ProfunKtor",
  startYear := Some(2017),
  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  homepage := Some(url("https://fs2-rabbit.profunktor.dev/")),
  headerLicense := Some(HeaderLicense.ALv2("2017-2019", "ProfunKtor")),
  scalacOptions ++= determineVersionSpecificDeps(scalaVersion.value).scalacOptions,
  libraryDependencies ++= {
    val library = determineVersionSpecificDeps(scalaVersion.value)
    Seq(
      compilerPlugin(library.kindProjector),
      compilerPlugin(library.betterMonadicFor),
      library.amqpClient,
      library.catsEffect,
      library.fs2Core,
      library.scalaTest % "test",
      library.scalaCheck % "test"
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
  pomIncludeRepository := { _ => false },
  pomExtra :=
      <developers>
        <developer>
          <id>gvolpe</id>
          <name>Gabriel Volpe</name>
          <url>https://github.com/gvolpe</url>
        </developer>
      </developers>
)

def CoreDependencies(scalaVersionStr: String): Seq[ModuleID] = {
  val library = determineVersionSpecificDeps(scalaVersionStr)
  Seq(
    library.logback % "test"
  )
}

def JsonDependencies(scalaVersionStr: String): Seq[ModuleID] = {
  val library = determineVersionSpecificDeps(scalaVersionStr)
  Seq(
    library.circeCore,
    library.circeGeneric,
    library.circeParser
  )
}

def ExamplesDependencies(scalaVersionStr: String): Seq[ModuleID] = {
  determineVersionSpecificDeps(scalaVersionStr) match {
    case library: Scala213Dependencies.type => Seq(library.logback % "runtime")
    case library: Scala212Dependencies.type =>
      Seq(
        library.logback % "runtime",
        library.monix,
        library.zioCore,
        library.zioCats
      )
    case library: Scala211Dependencies.type =>
      Seq(
        library.logback % "runtime",
        library.monix,
        library.zioCore,
        library.zioCats
      )
  }
}

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
  .settings(libraryDependencies ++= CoreDependencies(scalaVersion.value))
  .settings(parallelExecution in Test := false)
  .enablePlugins(AutomateHeaderPlugin)

lazy val `fs2-rabbit-circe` = project.in(file("json-circe"))
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= JsonDependencies(scalaVersion.value))
  .settings(parallelExecution in Test := false)
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(`fs2-rabbit`)

lazy val `fs2-rabbit-test-support` = project.in(file("test-support"))
  .settings(commonSettings: _*)
  .settings(libraryDependencies += determineVersionSpecificDeps(scalaVersion.value).scalaTest)
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
  .settings(libraryDependencies ++= ExamplesDependencies(scalaVersion.value))
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
    micrositeGitterChannelUrl := "profunktor-dev/fs2-rabbit",
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
addCommandAlias("buildFs2Rabbit", ";clean;+test;tut")

