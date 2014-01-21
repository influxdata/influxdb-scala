package org.influxdb.scala

/**
 * An untyped series has a name and a sequence of DataPoints.
 */
case class Series(name: String, time_precision: Precision, data: List[DataPoint])
