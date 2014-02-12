package org.influxdb.scala

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Success
import org.slf4j.LoggerFactory
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Failure
import java.util.Date
import scala.util.matching.Regex

class InfluxDB(hostName: String, port: Int, user: String, pwd: String, db: String) extends InfluxDBUntypedAPI {
  // require a HTTPServiceComponent and a JsonConverterComponent
  self: HTTPServiceComponent with JsonConverterComponent =>

  val baseUrl = s"http://$hostName:$port/db"
  val dbUrl = s"$baseUrl/${db.urlEncoded}"
  val seriesUrl = s"$dbUrl/series?u=${user.urlEncoded}&p=${pwd.urlEncoded}"
  val LOG = LoggerFactory.getLogger("org.influxdb.scala.InfluxDB.Client")

  private def urlForSeries(name:String) = s"$dbUrl/series/$name?u=${user.urlEncoded}&p=${pwd.urlEncoded}"

  /**
   * This currently requires the cluster admin credentials
   * @return
   */
  def listDatabases: Future[List[DBInfo]] = {
    val p = Promise[List[DBInfo]]
    val url = s"$baseUrl?u=${user.urlEncoded}&p=${pwd.urlEncoded}"
    LOG.debug(s"Getting databases from $url")
    httpService.GET(url) onComplete {
      case Success(response) => p.success(jsonConverter.jsonToDBInfo(response))
      case Failure(error) => p.failure(error)
    }
    p.future
  }

  /**
   * Execute the query asynchronously, resulting in a future QueryResult
   * Since an influx query can deliver results from multiple series, a QueryResult is a Seq[Series]
   * @param queryString influxdb query to execute
   * @param precision time precision (MILLIS, MICROS or SECONDS)
   * @return a Future[QueryResult] which contains either a Seq[Series] or a Failure
   */
  def query(queryString: String, precision: Precision): Future[QueryResult] = {
    val url = s"$seriesUrl&time_precision=${precision.qs}&q=${queryString.urlEncoded}"
    LOG.debug(s" getting data from url $url")
    val p = Promise[QueryResult]
    httpService.GET(url) onComplete {
      case Success(response) => jsonConverter.jsonToSeries(response, precision) match {
        case Success(qr) => p.success(qr)
        case Failure(error) => p.failure(error)
      }
      case Failure(error) => p.failure(error)
    }
    p.future
  }

  /**
   * single point insertion
   * @param seriesName name of the series to insert the point into
   * @param point Datapoint (a Map[String,Any]). If you need time to be added, have a "time"-> aDate in the map
   * @param precision MICROS,MILLIS or SECONDS; determines how the "time" column gets encoded
   * @return Future[Unit] only useful to check for errors using the Future's  onFailure functionality
   */
  def insertData(seriesName: String, point: DataPoint, precision: Precision): Future[Unit] = insertData(Series(seriesName, precision, List(point)))

  def insertData(series: Series): Future[Unit] = {
    val json = jsonConverter.seriesToJson(series)
    val url = s"$seriesUrl&time_precision=${series.time_precision.qs}"
    LOG.debug(s"submitting $json to $seriesUrl")
    httpService.POST(url, json, "application/json")
  }

  def dropSeries(name:String): Future[Unit] = {
    val url = urlForSeries(name)
    LOG.debug(s"Dropping series using url $url")
    httpService.DELETE(url)
  }

  def scheduleDelete(regex:Regex, olderThanDays: Int, runAtHour: Int): Future[Unit] = {
    val url = s"$dbUrl/scheduled_deletes"
    val json =
      s"""
        {
          "regex" : "${regex.toString}",
          "olderThan" : "${olderThanDays}d",
          "runAt" : $runAtHour
        }
      """.stripMargin
    LOG.debug (s"Scheduling delete $json at $url")
    httpService.POST(url, json, jsonContentType)
  }

  def scheduledDeletes: Future[List[String]] = {
    val url = s"$dbUrl/scheduled_deletes"
    val p = Promise[List[String]]
    httpService.GET(url) onComplete {
      case Failure(error) => p.failure(error)
      case Success(response) =>
    }
    p.future
  }
}
