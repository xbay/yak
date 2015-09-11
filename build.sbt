lazy val commonSettings = Seq(
  organization := "xbay.github.io",
  version := "0.1.0",
  scalaVersion := "2.11.7"
)

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    name := "yak",
    libraryDependencies ++= Seq(
      "org.scalaz" %% "scalaz-core" % "7.1.3",
      "org.specs2" %% "specs2-core" % "3.6.4" % "test",
      "com.twitter" % "finatra" % "1.4.1")
  )
