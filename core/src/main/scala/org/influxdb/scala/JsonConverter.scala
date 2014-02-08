package org.influxdb.scala

import scala.util.Try
import org.slf4j.LoggerFactory

object JsonConverterComponent {
  trait JsonConverter {

    val LOG = LoggerFactory.getLogger("org.influxdb.scala.JsonConverter")

    def jsonToSeries(response: String, precision: Precision): Try[QueryResult]
    def seriesToJson(s:Series):String

    /**
     * combine the keys for all points into a single list of column names.
     * points in the sequence may have different columns, and the values in the points array
     * for missing columns will be null
     */
    def allColumns(points: Seq[DataPoint]): List[String] =
      points.foldLeft(Set[String]())((acc, p) => acc ++ p.keys.toSet).toList


  }  
}

trait JsonConverterComponent {
  import JsonConverterComponent.JsonConverter
  val jsonConverter: JsonConverter
  
}