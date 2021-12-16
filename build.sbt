name := "kafka-to-elasticsearch"

organization := "com.myawesome"

version := "1.0.0"

scalaVersion := "2.13.3"

val versions = new {
  val logback = "1.2.7"
  val scalaTest = "3.2.5"
  val avro4s = "3.1.1"
  val kafka = "2.6.0"
  val confluent = "6.2.1"
  val elastic4s = "7.15.5"
}

Test / parallelExecution := false

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % versions.logback,
  "com.sksamuel.avro4s" %% "avro4s-core" % versions.avro4s,
  "org.apache.kafka" % "kafka-clients" % versions.kafka,
  "io.confluent" % "kafka-avro-serializer" % versions.confluent,
  "com.sksamuel.elastic4s" %% "elastic4s-client-esjava" % versions.elastic4s,
  "com.sksamuel.elastic4s" %% "elastic4s-json-jackson" % versions.elastic4s,
  "org.scalatest" %% "scalatest" % versions.scalaTest % Test
)

resolvers ++= Seq(
  Resolver.sonatypeRepo("public"),
  "Confluent Maven Repo" at "https://packages.confluent.io/maven/"
)

ThisBuild / assemblyMergeStrategy := {
  case PathList("module-info.class") => MergeStrategy.discard
  case x if x.endsWith("/module-info.class") => MergeStrategy.discard
  case x if x.startsWith("META-INF") => MergeStrategy.discard // Bumf
  case x if x.endsWith(".html") => MergeStrategy.discard // More bumf
  case x if x.contains("slf4j-api") => MergeStrategy.last
  case x if x.contains("org/cyberneko/html") => MergeStrategy.first
  case PathList("com", "esotericsoftware", xs@_ *) => MergeStrategy.last // For Log$Logger.class
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}

Compile / sourceGenerators += (Compile / avroScalaGenerate).taskValue

jibRegistry := "registry.myawesome.com"
jibCustomRepositoryPath := Some("docker/" + name.value)
