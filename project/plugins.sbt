resolvers += Classpaths.sbtPluginReleases
resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.geirsson"              % "sbt-ci-release"  % "1.5.7")
addSbtPlugin("de.heikoseeberger"         % "sbt-header"      % "5.6.0")
addSbtPlugin("com.47deg"                 % "sbt-microsites"  % "1.3.4")
addSbtPlugin("org.scalameta"             % "sbt-mdoc"        % "2.2.22")
addSbtPlugin("com.scalapenos"            % "sbt-prompt"      % "1.0.2")
addSbtPlugin("com.lucidchart"            % "sbt-scalafmt"    % "1.16")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"    % "0.1.20")
addSbtPlugin("com.timushev.sbt"          % "sbt-updates"     % "0.6.0")
addSbtPlugin("com.typesafe"              % "sbt-mima-plugin" % "1.0.0")
