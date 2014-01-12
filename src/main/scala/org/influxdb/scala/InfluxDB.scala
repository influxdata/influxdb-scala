package org.influxdb.scala

import scala.concurrent.Future
import scala.util.Try
import java.util.concurrent.Executor
import scala.concurrent.Promise
import org.json4s.native.JsonParser
import org.json4s.JsonAST._
import org.json4s.DefaultFormats
import org.json4s.native.Serialization.{ read, write => swrite }
import scala.util.Success
import java.net.URLEncoder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.annotation.tailrec
import java.util.Date
import scala.concurrent.ExecutionContext.Implicits.global
import org.influxdb.scala.macros.Macros.Mappable
import org.influxdb.scala.macros.Macros.Mappable._
import scala.concurrent.duration.Duration
import scala.util.Failure
import java.util.concurrent.ExecutorService

// Cake pattern, see http://jonasboner.com/2008/10/06/real-world-scala-dependency-injection-di/
trait InfluxDBClientComponent {
  this: HTTPServiceComponent =>
  
  val client: InfluxDBClient

  class InfluxDBClient(hostName: String, port: Int, user: String, pwd: String, db: String)(implicit pool: ExecutorService) {

    implicit lazy val formats = DefaultFormats

    // implicit conversions from typed to map-based series and vice-versa
    implicit def toDataPoint[T: Mappable](p: T) = implicitly[Mappable[T]].toMap(p)
    implicit def fromDataPoint[T: Mappable](p: DataPoint) = implicitly[Mappable[T]].fromMap(p)
    implicit def TSeriesToSeries[T: Mappable](ts: TSeries[T]): Series = Series(ts.name, ts.time_precision, ts.data.map(toDataPoint(_)))
    implicit def SeriesToTSeries[T: Mappable](s: Series): TSeries[T] = TSeries[T](s.name, s.time_precision, s.data.map(fromDataPoint(_)))
    implicit def QResultToTQResult[T: Mappable](qr: QueryResult): TQueryResult[T] = qr map (SeriesToTSeries(_))
    implicit def futQR2futTQR[T: Mappable](fqr: Future[QueryResult]): Future[TQueryResult[T]] = fqr map (QResultToTQResult(_))

    val urlPrefix = s"http://$hostName:$port/db/${db.urlEncoded}/series?u=${user.urlEncoded}&p=${pwd.urlEncoded}"

    val LOG = LoggerFactory.getLogger("org.influxdb.scala.InfluxDB")

    /**
     * Execute the query asynchronously, resulting in a future QueryResult
     * Since an influx query can deliver results from multiple series, a QueryResult is a Seq[Series]
     */
    def query(queryString: String, precision: Precision): Future[QueryResult] = {
      val url = s"$urlPrefix&time_precision=${precision.qs}&q=${queryString.urlEncoded}"
      LOG.debug(s" getting data from url $url")
      val p = Promise[QueryResult]
      val result = httpService.GET(url) onComplete {
        case Success(response) => jsonToSeries(response, precision) match {
          case Success(qr) => p.success(qr)
          case Failure(error) => p.failure(error)
        }
        case Failure(error) => p.failure(error)
      }
      p.future
    }

    // the magic of implicit conversions makes this a one-liner
    def queryAs[T: Mappable](queryString: String, precision: Precision): Future[TQueryResult[T]] = query(queryString, precision)

    /**
     * extracts the value for key c from a Map[String,Any] as a JValue of the appropriate type.
     * If key is not found returns JNull
     * @param point the data point
     * @param c column name
     * @precision time precision for values of type Date
     */
    private def pointValue(point: DataPoint, c: String, precision: Precision) = point.getOrElse(c, null) match {
      case i: Int => JInt(i)
      case d: Double => JDouble(d)
      case f: Float => JDouble(f)
      case d: BigDecimal => JDecimal(d)
      case i: BigInt => JInt(i)
      case s: String => JString(s)
      case b: Boolean => JBool(b)
      case d: Date => JInt(precision.toBigInt(d))
      case null => JNull
    }

    /**
     * transforms a DataPoint (Map[String,Any]) to a JArray of values. Size of result will be equal to size
     * of columns with null values for missing keys
     */
    private def collectValues(columns: List[String], point: DataPoint, precision: Precision): JArray = {
      val values = columns.map(c => pointValue(point, c, precision))
      JArray(values)
    }

    /**
     * single point insertion
     * @param seriesName name of the series to insert the point into
     * @param point Datapoint (a Map[String,Any]). If you need time to be added, have a "time"-> aDate in the map
     * @param precision MICROS,MILLIS or SECONDS; determines how the "time" column gets encoded
     */
    def insertData(seriesName: String, point: DataPoint, precision: Precision): Future[Unit] = {
      val colNames = point.keys.toList
      insertData(seriesName, colNames, List(collectValues(colNames, point, precision)), precision)
    }

    def insertDataFrom[T: Mappable](seriesName: String, point: T, precision: Precision): Future[Unit] = {
      val dp: DataPoint = point
      insertData(seriesName, dp, precision)
    }

    /**
     * combine the keys for all points into a single list of column names.
     * points in the sequence may have different columns, and the values in the points array
     * for missing columns will be null
     */
    private def allColumns(points: Seq[DataPoint]): List[String] =
      points.foldLeft(Set[String]())((acc, p) => acc ++ p.keys.toSet).toList

    private def insertData(name: String, columns: List[String], points: List[JArray], precision: Precision): Future[Unit] = {
      val series = JObject(
        "name" -> JString(name),
        "columns" -> JArray(columns.map(c => JString(c))),
        "points" -> JArray(points))
      val json = swrite(List(series))
      val url = s"$urlPrefix&time_precision=${precision.qs}"
      LOG.debug(s"submitting $json to $urlPrefix")
      httpService.POST(url, json, "application/json")
    }

    def insertData(series: Series): Future[Unit] = {
      val columns = allColumns(series.data)
      val points = series.data.map(point => collectValues(columns, point, series.time_precision))
      println(swrite(points))
      insertData(series.name, columns, points, series.time_precision)
    }

    // implicit conversion takes care of the hard stuff
    def insertDataFrom[T: Mappable](series: TSeries[T]): Future[Unit] = insertData(series)

    // convert a json response to an instance of QueryResult. May fail, hence the Try
    private def jsonToSeries(response: String, precision: Precision): Try[QueryResult] = {
      LOG.debug(s"received: $response")

      // create a Map from a Seq of (k,v) pairs
      @tailrec
      def rmap[A, B](in: Seq[(A, B)], out: Map[A, B] = Map[A, B]()): Map[A, B] = in match {
        case (e @ (k, v)) :: tail if out.getOrElse(k, v) == v => rmap(tail, out + e)
        case Nil => out
      }

      // build a Series instance from the given name, columns and data points
      def constructSeries(name: String, cols: Seq[String], points: List[JValue]): Series = {
        // convert JArray to List
        val ps = for { JArray(point) <- points } yield point
        // create List[Seq[(colname -> value)]] 
        val r = ps.map(p => cols.zipWithIndex.map { case (col, index) => (col -> p(index)) })

        // maps the untyped JValue to its proper type (time becomes a java.util.Date)
        def extractValue(k: String, v: JValue): Any = {
          v match {
            case JInt(anInt) if (k == "time") => precision.toDate(anInt)
            case JInt(anInt) => Some(anInt) 
            case JString(aString) => Some(aString)
            case JBool(aBool) => Some(aBool)
            case _ => None
          }
        }

        val maps = r map (e => rmap[String, Any](e map (l => (l._1, extractValue(l._1, l._2)))))
        Series(name, precision, maps)
      }

      try {
        val json = JsonParser.parse(response)
        val result = for {
          JObject(seriesData) <- json
          JField("name", JString(name)) <- seriesData
          JField("columns", JArray(cols)) <- seriesData
          JField("points", JArray(points)) <- seriesData
        } yield constructSeries(name, for { JString(s) <- cols } yield s, points)
        Success(result)
      } catch {
        case t: Throwable => Failure(t)
      }
    }
  }
}