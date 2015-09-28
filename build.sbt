lazy val commonSettings = Seq(
  organization := "xbay.github.io",
  version := "0.0.1-SNAPSHOT",
  scalaVersion := "2.11.7"
)

resolvers += "Twitter" at "http://maven.twttr.com"

lazy val versions = new {
  val finatra = "2.0.0"
  val jodaTime = "2.8.2"
  val logback = "1.1.3"
  val scalaz = "7.1.3"
  val spec2 = "3.6.4"
  val akka = "2.3.13"
}

assemblyMergeStrategy in assembly := {
    case PathList(ps @ _*) if ps.last endsWith "BUILD" => MergeStrategy.first
    case x =>
       val oldStrategy = (assemblyMergeStrategy in assembly).value
       oldStrategy(x)
}

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    name := "yak",
    libraryDependencies ++= Seq(
      "org.scalaz" %% "scalaz-core" % versions.scalaz,
      "org.specs2" %% "specs2-core" % versions.spec2 % "test",
      "ch.qos.logback" % "logback-classic" % versions.logback,
      "joda-time" % "joda-time" % versions.jodaTime,

      "com.typesafe.akka" %% "akka-actor" % versions.akka,
      "com.typesafe.akka" %% "akka-contrib" % versions.akka,
      "com.typesafe.akka" %% "akka-agent" % versions.akka,
      "com.typesafe.akka" %% "akka-kernel" % versions.akka,
      "com.typesafe.akka" %% "akka-remote" % versions.akka,
      "com.typesafe.akka" %% "akka-slf4j" % versions.akka,
      "com.typesafe.akka" %% "akka-testkit" % versions.akka,

      "com.twitter.finatra" % "finatra-root_2.11" % versions.finatra,
      "com.twitter.finatra" % "finatra-http_2.11" % versions.finatra,
      "com.twitter.finatra" % "finatra-slf4j_2.11" % versions.finatra,
      "com.twitter.finatra" % "finatra-jackson_2.11" % versions.finatra,
      "com.twitter.finatra" % "finatra-utils_2.11" % versions.finatra,
      "com.twitter.finatra" % "finatra-httpclient_2.11" % versions.finatra,

      "com.twitter.inject" % "inject-server_2.11" % versions.finatra,
      "com.twitter.inject" % "inject-app_2.11" % versions.finatra,
      "com.twitter.inject" % "inject-modules_2.11" % versions.finatra,
      "com.twitter.inject" % "inject-core_2.11" % versions.finatra
    )
  )
