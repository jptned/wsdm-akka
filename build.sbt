name := "akka-quickstart-scala"

version := "1.0"

scalaVersion := "2.13.1"

lazy val akkaVersion = "2.6.5"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.1.0" % Test,
  "com.typesafe.akka" %% "akka-http"   % "10.1.12",
  "com.typesafe.akka" %% "akka-stream" % "2.6.5",
  "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % "2.6.5" % Test,
  "com.typesafe.akka" %% "akka-http-testkit" % "10.1.12" % Test,
  "com.typesafe.play" %% "play-json" % "2.8.1"
)
