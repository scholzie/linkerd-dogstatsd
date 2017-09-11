resolvers += "twitter-repo" at "https://maven.twttr.com"
ivyScala := ivyScala.value.map(_.copy(overrideScalaVersion = true))

// formatting
addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.6.0")

// doc generation
addSbtPlugin("com.eed3si9n"   % "sbt-unidoc"    % "0.3.3")

// packaging
addSbtPlugin("com.eed3si9n"      % "sbt-assembly"  % "0.14.1")
