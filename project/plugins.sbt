resolvers += Classpaths.sbtPluginReleases
resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.github.sbt"            % "sbt-ci-release"  % "1.5.10")
addSbtPlugin("de.heikoseeberger"         % "sbt-header"      % "5.9.0")
addSbtPlugin("com.47deg"                 % "sbt-microsites"  % "1.4.1")
addSbtPlugin("org.scalameta"             % "sbt-mdoc"        % "2.3.6")
addSbtPlugin("com.scalapenos"            % "sbt-prompt"      % "1.0.2")
addSbtPlugin("org.scalameta"             % "sbt-scalafmt"    % "2.5.0")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"    % "0.4.1")
addSbtPlugin("com.timushev.sbt"          % "sbt-updates"     % "0.6.4")
addSbtPlugin("com.typesafe"              % "sbt-mima-plugin" % "1.1.1")
