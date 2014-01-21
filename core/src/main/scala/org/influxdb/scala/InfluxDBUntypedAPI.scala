package org.influxdb.scala

import scala.concurrent.Future

trait InfluxDBUntypedAPI {

  /**
     * Execute the query asynchronously, resulting in a future QueryResult
     * Since an influx query can deliver results from multiple series, a QueryResult is a Seq[Series]
     */
    def query(queryString: String, precision: Precision): Future[QueryResult]
    
    /**
     * single point insertion
     * @param seriesName name of the series to insert the point into
     * @param point Datapoint (a Map[String,Any]). If you need time to be added, have a "time"-> aDate in the map
     * @param precision MICROS,MILLIS or SECONDS; determines how the "time" column gets encoded
     */
    def insertData(seriesName: String, point: DataPoint, precision: Precision): Future[Unit]
    
    def insertData(series: Series): Future[Unit]
}