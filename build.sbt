name := "akka-io-file"

organization := "io.github.drexin"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.10.3"

parallelExecution in Test := false

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.0-RC3" % "compile"

libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.3.0-RC3" % "test"

libraryDependencies += "org.scalatest" % "scalatest_2.10" % "2.0" % "test"
