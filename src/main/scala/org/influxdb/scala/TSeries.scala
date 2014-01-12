package org.influxdb.scala

  /**
   * A TSeries (typed series) has a name, precision and a sequence of objects of type T specified
   * by the application
   * @see queryAs[T]
   */
  case class TSeries[T](name: String, time_precision: Precision, data: List[T])
