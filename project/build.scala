import sbt._
import Keys._

object InfluxDBClientBuild extends Build {
  val Organization = "org.influxdb"
  val Name = "InfluxDB Client"
  val Version = "0.1.0-SNAPSHOT"
  val ScalaVersion = "2.10.3"

  lazy val project = Project (
    "influxdb-scala",
    file("."),
    settings = Defaults.defaultSettings ++ Seq(
      organization := Organization,
      name := Name,
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      resolvers += "Apache repo"   at "https://repository.apache.org/content/repositories/releases",
      libraryDependencies ++= Seq(        
        "org.json4s" %% "json4s-jackson" % "3.2.6",
        "com.ning" % "async-http-client" % "1.0.0",

        "org.scalatest" %% "scalatest" % "2.1.0" % "test"
      )
    )
  )
}

