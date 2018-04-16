import sbt.Resolver

name := "CollaborativeFiltering"

version := "0.1"

scalaVersion := "2.11.11"

resolvers ++= Seq(
  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  Resolver.bintrayRepo("hseeberger", "maven")
)

libraryDependencies ++= Seq(
  // the following dependency is unmanaged.
  // "com.oracle.jdbc" % "ojdbc6" % "12.1.0.2",
  "org.xerial" % "sqlite-jdbc" % "3.20.0" % "test",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",

  // korean text tokenizer
  "org.bitbucket.eunjeon" %% "seunjeon" % "1.3.0"
    exclude("org.slf4j", "slf4j-jdk14"),

  // time-related packages
  "com.github.nscala-time" %% "nscala-time" % "2.16.0",

  // fastutil (efficient collection library)
  "it.unimi.dsi" % "fastutil" % "8.1.0",

  // config and cmd opts library
  "com.iheart" %% "ficus" % "1.4.3",
  "com.github.scopt" %% "scopt" % "3.7.0",

  // logging library
  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",

  // akka (for logging server)
  "com.typesafe.akka" %% "akka-http" % "10.0.10",
  "com.typesafe.akka" %% "akka-http-core" % "10.0.10",

  // JSON serialization
  "io.circe" %% "circe-core" % "0.8.0",
  "io.circe" %% "circe-generic" % "0.8.0",
  "io.circe" %% "circe-parser" % "0.8.0",
  "io.circe" %% "circe-generic-extras" % "0.8.0",

  // Sugar for serialization and deserialization in akka-http with circe
  "de.heikoseeberger" %% "akka-http-circe" % "1.18.1",

  // HBase
  "org.apache.hbase" % "hbase-common" % "1.2.6",
  "org.apache.hbase" % "hbase-client" % "1.2.6",

  // hadoop and spark
  "org.apache.hadoop" % "hadoop-client" % "2.7.4" % "provided",
  "org.apache.spark" %% "spark-core" % "2.2.0",
  "org.apache.spark" %% "spark-mllib" % "2.2.0",
  "org.apache.spark" %% "spark-sql" % "2.2.0"

//  "org.apache.spark" %% "spark-core" % "2.2.0" % "provided",
//  "org.apache.spark" %% "spark-mllib" % "2.2.0" % "provided",
//  "org.apache.spark" %% "spark-sql" % "2.2.0" % "provided"
)