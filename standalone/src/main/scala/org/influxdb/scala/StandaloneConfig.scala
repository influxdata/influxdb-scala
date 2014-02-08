package org.influxdb.scala

/**
 * Combines the AsyncHttpClientComponent and Json4sJsonConverterComponent traits
 * for a complete configuration of a standalone influxdb client using the asyncHttp client
 * and json4s converters.
 * See http://jonasboner.com/2008/10/06/real-world-scala-dependency-injection-di/
 */
trait StandaloneConfig extends AsyncHttpClientComponent with Json4sJsonConverterComponent