name := "wsdm-akka"

version := "1.0"

scalaVersion := "2.13.1"

fork := true

lazy val akkaVersion = "2.6.5"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.1.0" % Test,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-typed"      % akkaVersion,
  "com.typesafe.akka" %% "akka-http"   % "10.1.12",
  "com.typesafe.akka" %% "akka-stream" % "2.6.5",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.12",
  "com.typesafe.akka" %% "akka-stream-testkit" % "2.6.5" % Test,
  "com.typesafe.akka" %% "akka-http-testkit" % "10.1.12" % Test,
  "com.typesafe.akka" %% "akka-distributed-data" % akkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson"  % akkaVersion,
  "org.iq80.leveldb"            % "leveldb"          % "0.7",
  "org.fusesource.leveldbjni"   % "leveldbjni-all"   % "1.8",
  "com.typesafe.play" %% "play-json" % "2.8.1",
  "com.typesafe.akka" %% "akka-discovery" % akkaVersion,
  "com.lightbend.akka.management" %% "akka-management" % "1.0.8",
  "com.lightbend.akka.management" %% "akka-management-cluster-http" % "1.0.8",
  "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % "1.0.8",
  "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % "1.0.8"
)

enablePlugins(JavaAppPackaging, DockerPlugin)
mainClass in Compile := Some("microservice.Webserver")

dockerBaseImage := "adoptopenjdk:11-jre-hotspot"
dockerUpdateLatest := true
dockerUsername := sys.props.get("docker.username")
dockerRepository := sys.props.get("docker.registry")
dockerExposedPorts := Seq(8080, 8558, 25520)