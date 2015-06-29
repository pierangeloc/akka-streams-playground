name := "akka-streams-playground"

version := "1.0"

scalaVersion := "2.11.7"


libraryDependencies ++= {
  val akkaV       = "2.3.10"
  val akkaStreamV = "1.0-RC4"
  val scalaTestV  = "2.2.4"
  Seq(
    "com.typesafe.akka" %% "akka-actor"                           % akkaV,
    "com.typesafe.akka" %% "akka-stream-experimental"             % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-experimental"               % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-core-experimental"          % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-xml-experimental"           % akkaStreamV,
    "org.scalatest"     %% "scalatest"                            % scalaTestV % "test",
    "org.json4s"        %% "json4s-jackson" % "3.2.11"
  )
}
