ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.12"
ThisBuild / javacOptions ++= Seq("-encoding", "UTF-8")
ThisBuild / scalacOptions ++= Seq("-encoding", "UTF-8")
lazy val root = (project in file("."))
  .settings(
    name := "projet_prf"
  )

val AkkaVersion = "2.6.20"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream"      % AkkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
  "ch.qos.logback"     % "logback-classic"  % "1.2.11",
  "org.scalatest"     %% "scalatest"        % "3.2.15" % Test
)