resolvers += Classpaths.sbtPluginReleases
resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.geirsson"              % "sbt-ci-release" % "1.5.4")
addSbtPlugin("de.heikoseeberger"         % "sbt-header"     % "5.6.0")
addSbtPlugin("com.47deg"                 % "sbt-microsites" % "1.2.1")
addSbtPlugin("com.scalapenos"            % "sbt-prompt"     % "1.0.2")
addSbtPlugin("com.lucidchart"            % "sbt-scalafmt"   % "1.16")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"   % "0.1.14")
addSbtPlugin("com.timushev.sbt"          % "sbt-updates"    % "0.5.1")
