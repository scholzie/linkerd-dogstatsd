def twitterUtil(mod: String) =
  "com.twitter" %% s"util-$mod" %  "6.45.0"

def finagle(mod: String) =
  "com.twitter" %% s"finagle-$mod" % "6.45.0"

def telemetery(mod: String) =
  "io.buoyant" %% s"telemetry-$mod" % "1.1.1"

def scalatest() =
  "org.scalatest" %% "scalatest" % "3.0.1"

def statsd() =
  "com.datadoghq" % "java-dogstatsd-client" % "2.3"

val `linkerd-dogstatsd` =
  project.in(file("."))
    .settings(
      organization := "com.blueapron",
      version := "0.0.1",
      scalaVersion in GlobalScope := "2.12.1",
      ivyScala := ivyScala.value.map(_.copy(overrideScalaVersion = true)),
      resolvers ++= Seq(
        "twitter-repo" at "https://maven.twttr.com",
        Resolver.mavenLocal,
        "typesafe" at "https://repo.typesafe.com/typesafe/releases"
      ),
      libraryDependencies ++= Seq(
        finagle("core"),
        statsd(),
        telemetery("core"),
        twitterUtil("stats"),
        scalatest() % "test"
      ),
      aggregate in assembly := false,
      assemblyMergeStrategy in assembly := {
        case "BUILD" => MergeStrategy.discard
        case "com/twitter/common/args/apt/cmdline.arg.info.txt.1" => MergeStrategy.discard
        case "META-INF/io.netty.versions.properties" => MergeStrategy.last
        case path => (assemblyMergeStrategy in assembly).value(path)
      },
      assemblyJarName in assembly := s"${name.value}-${version.value}.jar",
      assemblyOutputPath in assembly := file(s"plugins/${(assemblyJarName in assembly).value}")
    )
