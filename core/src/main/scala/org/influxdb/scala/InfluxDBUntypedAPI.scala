package org.influxdb.scala

import scala.concurrent.Future
import scala.util.matching.Regex

trait InfluxDBAdminAPI {
  /**
   * Return a list of databases
   * @return List[DBInfo] list of databases
   */
  def listDatabases: Future[List[DBInfo]]

  /**
   * Drop the series with the given name
   * @param name name f the series to drop
   */
  def dropSeries(name:String): Future[Unit]

  /**
   * List all scheduled deletes
   * @return
   */
  def scheduledDeletes: Future[List[String]]

  /**
   * Create a scheduled delete
   * @param regex         regular expression to define all series t delete from
   * @param olderThanDays delete data older than given number of days
   * @param runAtHour     run the delete at this given hour
   */
  def scheduleDelete(regex:Regex, olderThanDays: Int, runAtHour: Int): Future[Unit]

}

trait InfluxDBUntypedDataAPI {

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

  /**
   * Insert all data from the given series
   * @param series series to insert
   */
  def insertData(series: Series): Future[Unit]

}

trait InfluxDBContinuousQueriesAPI {

  def listContinuousQueries: Future[List[ContinuousQuery]]
  def createContinuousQuery(query:String): Future[Unit]
  def deleteContinuousQuery(cq:ContinuousQuery):Future[Unit]
  def deleteContinuousQuery(id:Int):Future[Unit]
}