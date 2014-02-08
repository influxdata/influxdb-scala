package org.influxdb.scala

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Success
import org.slf4j.LoggerFactory
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Failure

class Client(hostName: String, port: Int, user: String, pwd: String, db: String) extends InfluxDBUntypedAPI {
  this: HTTPServiceComponent with JsonConverterComponent =>
  val baseUrl = s"http://$hostName:$port/db"
  val urlPrefix = s"$baseUrl/${db.urlEncoded}/series?u=${user.urlEncoded}&p=${pwd.urlEncoded}"
  val LOG = LoggerFactory.getLogger("org.influxdb.scala.InfluxDB.Client")


  /**
   * Execute the query asynchronously, resulting in a future QueryResult
   * Since an influx query can deliver results from multiple series, a QueryResult is a Seq[Series]
   */
  def query(queryString: String, precision: Precision): Future[QueryResult] = {
    val url = s"$urlPrefix&time_precision=${precision.qs}&q=${queryString.urlEncoded}"
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
   */
  def insertData(seriesName: String, point: DataPoint, precision: Precision): Future[Unit] = insertData(Series(seriesName, precision, List(point)))

  def insertData(series: Series): Future[Unit] = {
    val json = jsonConverter.seriesToJson(series)
    val url = s"$urlPrefix&time_precision=${series.time_precision.qs}"
    LOG.debug(s"submitting $json to $urlPrefix")
    httpService.POST(url, json, "application/json")
  }
}
