import sbt._
import Keys._

object InfluxBuild extends Build {
  
  // the core API is compatible with scala 2.10+ and only depends on slf4j
  lazy val core = Project("core", file("core")) settings(
      version := "0.1",
      scalaVersion := "2.10.3",
      crossScalaVersions := Seq("2.10.3","2.11.0-M7"),
      libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.6.4"
  )
  
  // the standalone extension of the core API has implementations of HTTPService and JsonConverter
  // that use async-http-client and json4s. In a Play! framework application, you may want to provide
  // different implementations based on WS and the json macros available in play to avoid these redundant
  // dependencies
  lazy val standalone = Project("standalone", file("standalone")) dependsOn(core) settings(
      version := "0.1",
      scalaVersion := "2.10.3",
      crossScalaVersions := Seq("2.10.3","2.11.0-M7"),
      mainClass := Some("TestApp"),
      libraryDependencies ++= Seq(
    		  "org.json4s" % "json4s-native_2.10" % "3.2.6",
    		  "com.ning" % "async-http-client" % "1.7.19"
      )
  )

  //============================================================================
  // Comment out the following two sub projects if you have issues building this
  //============================================================================

  /* I give up... WTF is going on here?????????
   * 
  // this project contains the Map[String,Any] <-> case class mapping macro abd only builds with scala 2.11
  lazy val macro = Project("macro", file("macro")) settings(
      version := "0.1",
      scalaVersion := "2.11.0-M7",
      libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-compiler" % _),
      libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-reflect" % _)
  )


  // this project contains the typed API which depends on both the core untyped API as well as the macros
  // which unfortunately only compile with the 2.11 milestone of scala
  lazy val experimental = Project("experimental", file("experimental")) dependsOn(core, macro, standalone) settings(
      version := "0.1",
      scalaVersion := "2.11.0-M7"
  )
  */
}
