import sbt._
import Keys._

object InfluxBuild extends Build {
  
  lazy val main = Project("main", file(".")) dependsOn(macroSub) settings(
      version := "0.1",
      scalaVersion := "2.11.0-M5",
      libraryDependencies ++= Seq(
    		  "org.json4s" % "json4s-native_2.10" % "3.2.5",
    		  "com.ning" % "async-http-client" % "1.7.19"
      )
  )
  
  lazy val macroSub = Project("macro", file("macro")) settings(
      version := "0.1",
      scalaVersion := "2.11.0-M5",
      libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-compiler" % _),
      //resolvers += Resolver.sonatypeRepo("snapshots"),
      libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _)//,
      //addCompilerPlugin("org.scala-lang.plugins" % "macro-paradise" % "2.0.0-SNAPSHOT" cross CrossVersion.full)
  )
}
