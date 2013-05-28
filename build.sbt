name := "Distributed Spectral Dimensionality Reduction"

version := "0.1"

scalaVersion := "2.9.2"

organization := "edu.berkeley.cs.amplab"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "1.6.1" % "test",
  "org.spark-project" % "spark-core_2.9.2" % "0.7.0",
  "org.scalanlp" % "breeze-math_2.9.2" % "0.1",
  "org.scalanlp" % "breeze-viz_2.9.2" % "0.1"
)

resolvers ++= Seq(
  "Typesafe" at "http://repo.typesafe.com/typesafe/releases",
  "Scala Tools Snapshots" at "http://scala-tools.org/repo-snapshots/",
  "ScalaNLP Maven2" at "http://repo.scalanlp.org/repo",
  "Spray" at "http://repo.spray.cc"
)
