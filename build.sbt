import sbt.Resolver

name := "CollaborativeFiltering"

version := "0.1"

scalaVersion := "2.11.11"

resolvers ++= Seq(
  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  Resolver.bintrayRepo("hseeberger", "maven")
)

libraryDependencies ++= Seq(

  // time-related packages
  "com.github.nscala-time" %% "nscala-time" % "2.16.0",

  // hadoop and spark
  "org.apache.hadoop" % "hadoop-client" % "2.7.4" % "provided",
  "org.apache.spark" %% "spark-core" % "2.2.0",
  "org.apache.spark" %% "spark-mllib" % "2.2.0",
  "org.apache.spark" %% "spark-sql" % "2.2.0"

//  "org.apache.spark" %% "spark-core" % "2.2.0" % "provided",
//  "org.apache.spark" %% "spark-mllib" % "2.2.0" % "provided",
//  "org.apache.spark" %% "spark-sql" % "2.2.0" % "provided"
)