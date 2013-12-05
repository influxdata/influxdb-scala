name := "Influxdb-scala"

version := "0.1"

scalaVersion := "2.10.3"

libraryDependencies ++= Seq(
  "org.scalautils" % "scalautils_2.10" % "2.0",
  "org.scalatest" % "scalatest_2.10" % "2.0" % "test",
  "com.netflix.rxjava" % "rxjava-scala" % "0.15.0",
  "org.json4s" % "json4s-native_2.10" % "3.2.5",
  "com.ning" % "async-http-client" % "1.7.19",
  "org.scala-lang.modules" %% "scala-async" % "0.9.0-M4"
)
