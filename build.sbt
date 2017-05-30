name := """fs2-rabbit"""

version := "0.0.4-SNAPSHOT"

scalaVersion := "2.11.8"

lazy val circeVersion = "0.5.1"

val commonSettings = Seq(
  organization := "com.github.gvolpe",
  licenses +=("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
  homepage := Some(url("https://github.com/gvolpe/fs2-rabbit")),
  //releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  scalaVersion := "2.11.8",
  libraryDependencies ++= Seq(
    "com.rabbitmq"    %  "amqp-client"      % "4.1.0",
    "co.fs2"          %% "fs2-core"         % "0.9.6",
    "io.circe"        %% "circe-core"       % circeVersion,
    "io.circe"        %% "circe-generic"    % circeVersion,
    "io.circe"        %% "circe-parser"     % circeVersion,
    "com.typesafe"    % "config"            % "1.3.1",
    "ch.qos.logback"  %  "logback-classic"  % "1.0.6" % "runtime",
    "org.scalatest"   %% "scalatest"        % "2.2.4" % "test"
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

lazy val root = project.in(file("."))
  .settings(commonSettings: _*)

sonatypeProfileName := "com.github.gvolpe"

publishArtifact := true

//resolvers += Resolver.sonatypeRepo("releases")
//addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
