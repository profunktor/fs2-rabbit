resolvers += Classpaths.sbtPluginReleases
resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.github.sbt"              % "sbt-ci-release"  % "1.5.10")
addSbtPlugin("de.heikoseeberger"         % "sbt-header"      % "5.6.0")
addSbtPlugin("com.47deg"                 % "sbt-microsites"  % "1.3.4")
addSbtPlugin("org.scalameta"             % "sbt-mdoc"        % "2.2.24")
addSbtPlugin("com.scalapenos"            % "sbt-prompt"      % "1.0.2")
addSbtPlugin("org.scalameta"             % "sbt-scalafmt"    % "2.4.6")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"    % "0.1.20")
addSbtPlugin("com.timushev.sbt"          % "sbt-updates"     % "0.6.1")
addSbtPlugin("com.typesafe"              % "sbt-mima-plugin" % "1.0.1")
