resolvers += Classpaths.sbtPluginReleases
resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.github.sbt"   % "sbt-ci-release"  % "1.9.3")
addSbtPlugin("com.github.sbt"   % "sbt-header"      % "5.11.0")
addSbtPlugin("com.47deg"        % "sbt-microsites"  % "1.4.4")
addSbtPlugin("org.scalameta"    % "sbt-mdoc"        % "2.8.0")
addSbtPlugin("org.scalameta"    % "sbt-scalafmt"    % "2.5.5")
addSbtPlugin("org.typelevel"    % "sbt-tpolecat"    % "0.5.2")
addSbtPlugin("com.timushev.sbt" % "sbt-updates"     % "0.6.4")
addSbtPlugin("com.typesafe"     % "sbt-mima-plugin" % "1.1.4")
