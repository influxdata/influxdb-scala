package org.influxdb.scala

import scala.concurrent.Future
import InfluxDB._
import scala.util.Try
import com.ning.http.client.AsyncHttpClient
import java.util.concurrent.Executor
import scala.concurrent.Promise
import org.json4s.native.JsonParser
import org.json4s.JsonAST._
import org.json4s.DefaultFormats
import org.json4s.native.Serialization.{read, write => swrite}
import scala.util.Success
import java.net.URLEncoder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.annotation.tailrec
import java.util.Date
import java.util.concurrent.{Executors,ExecutorService}
import scala.concurrent.ExecutionContext.Implicits.global
import org.influxdb.scala.macros.Macros.Mappable
import org.influxdb.scala.macros.Macros.Mappable._
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.util.Failure

class InfluxDB(hostName: String, port: Int, user: String, pwd: String, db:String) {
  
  implicit lazy val formats = DefaultFormats
  implicit lazy val pool = Executors.newSingleThreadExecutor()
  val client = new AsyncHttpClient()
  val urlPrefix = s"http://$hostName:$port/db/${db.urlEncoded}/series?u=${user.urlEncoded}&p=${pwd.urlEncoded}"
  
  val LOG = LoggerFactory.getLogger("InfluxDB")
  
  def shutdown(timeout: Duration) {
    pool.awaitTermination(timeout.length, timeout.unit)
    pool.shutdown()
    client.close()
  }
  
  /**
   * Execute the query asynchronously, resulting in a future QueryResult
   * Since an influx query can deliver results from multiple series, a QueryResult is a Seq[Series]
   */
  def query(queryString:String, precision: Precision): Future[QueryResult] = {
    val url = s"$urlPrefix&time_precision=${precision.qs}&q=${queryString.urlEncoded}"
    LOG.debug(s"url = $url")
    val f = client.prepareGet(url).execute()
    val p = Promise[QueryResult]()
    f.addListener(new Runnable() {
      def run = {
    	val response = f.get
    	if (response.getStatusCode() < 400) {
    	  p.complete(jsonToSeries(response.getResponseBody(), precision))
    	} else {
    	  p.failure(
    	      new RuntimeException(s"Error response: ${response.getStatusCode()}: ${response.getResponseBody()}"))
    	}
      }
    }, pool)
    p.future
   }
   
   def queryAs[T](queryString:String, precision: Precision)(implicit mapper: Mappable[T]): Future[TQueryResult[T]] = {
     // TODO figure out how to get context bounds to work and use implicitly instead of currying
     query(queryString, precision) map {qr => 
       qr map {series => 
         TSeries[T](series.name, precision, series.data.map(p => mapper.fromMap(p)))
       }
     }
   }
   
   /**
    * extracts the value for key c from a Map[String,Any] as a JValue of the appropriate type.
    * If key is not found returns JNull
    * @param point the data point 
    * @param c column name
    * @precision time precision for values of type Date
    */
   private def pointValue(point: DataPoint, c: String, precision:Precision) = point.getOrElse(c, null) match {
 	  case i:Int => JInt(i)
 	  case d:Double => JDouble(d)
 	  case f:Float => JDouble(f)
 	  case d:BigDecimal => JDecimal(d)
 	  case i:BigInt => JInt(i)
 	  case s:String => JString(s)
 	  case b:Boolean => JBool(b)
 	  case d: Date => JInt(precision.toBigInt(d))
 	  case null => JNull
   }
   
   /**
    * transforms a DataPoint (Map[String,Any]) to a JArray of values. Size of result will be equal to size
    * of columns with null values for missing keys
    */
   private def collectValues(columns:List[String], point: DataPoint, precision:Precision) : JArray = {
	  val values = columns.map ( c => pointValue(point, c, precision))
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
   
   def insertDataFrom[T](seriesName: String, point: T): Future[Try[Unit]] = {
???
   }
   
   /**
    * combine the keys for all points into a single list of column names.
    * points in the sequence may have different columns, and the values in the points array
    * for missing columns will be null
    */
   private def allColumns(points: Seq[DataPoint]) : List[String] = 
	  	points.foldLeft(Set[String]())((acc, p) => acc ++ p.keys.toSet).toList
   
   
   private def insertData(name:String, columns:List[String], points: List[JArray], precision: Precision): Future[Unit] = {
     val series = JObject(
         "name"-> JString(name),
         "columns" -> JArray(columns.map(c=>JString(c))),
         "points" -> JArray(points)
     )
     val json = swrite(List(series))
     val url = s"$urlPrefix&time_precision=${precision.qs}"         
     println(s"submitting $json to $urlPrefix")
     val p = Promise[Unit]()
     val f = client.preparePost(url).setBody(json).addHeader("Content-Type", "application/json").execute
     f.addListener(new Runnable () {
       def run {
	        val response = f.get
	    	if (response.getStatusCode() < 400) {
	    	  p.success()
	    	} else {
	    	  p.failure(
	    	      new RuntimeException(s"Error response: ${response.getStatusCode()}: ${response.getResponseBody()}"))
	    	}
       }
     }, pool)
     p.future
     
   }
   
   def insertData(series:Series):Future[Unit] = {
	  val columns = allColumns(series.data)
	  println(s"combined columns: $columns")
	  val points = series.data.map(point => collectValues(columns, point, series.time_precision))
	  println(swrite(points))
	  insertData(series.name, columns, points, series.time_precision)
   }
   
   def insertDataFrom[T](series:TSeries[T]):Future[Try[Unit]] = {
???     
   }
   
   private def jsonToSeries(response: String, precision: Precision): Try[QueryResult] = {
      LOG.debug(s"received: $response")
      
      /**
       * create a Map from a Seq of (k,v) pairs
       */
      @tailrec
	  def rmap[A,B](in: Seq[(A,B)], out: Map[A,B] = Map[A,B]() ): Map[A,B] = in match {
	     case (e @ (k,v)) :: tail if out.getOrElse(k,v) == v => rmap(tail, out + e)
	     case Nil => out
	  }
      
      /**
       * build a Series instance from the given name, columns and data points
       */
      def constructSeries(name:String, cols: Seq[String], points: List[JValue]): Series = {
        // convert JArray to List
        val ps = for {JArray(point) <-points} yield point
        // create List[Seq[(colname -> value)]] 
        val r = ps.map (p => cols.zipWithIndex.map {case (col, index) => (col -> p(index)) })
        
        // maps the untyped JValue to its proper type (time becomes a java.util.Date)
        def extractValue(k: String, v : JValue):Any = {
          v match {
            case JInt(anInt) if (k=="time") => precision.toDate(anInt)
            case JInt(anInt) => anInt // TODO do we want to keep BigInt here?
            case JString(aString) => aString
            case JBool(aBool) => aBool
            case _ => None
          }
        }
        
        val maps = r map (e => rmap[String,Any](e map (l => (l._1, extractValue(l._1,l._2)))) )
        Series(name, precision, maps)
      }
 
      try {
      	val json = JsonParser.parse(response)
      	val result = for {
            JObject(seriesData) <- json
            JField("name",JString(name)) <- seriesData
            JField("columns", JArray(cols)) <- seriesData
            JField("points", JArray(points)) <- seriesData 
        } yield constructSeries(name, for {JString(s) <- cols} yield s, points)
        Success(result)
      } catch {
        case t: Throwable => Failure(t)
      }
   }
}

object InfluxDB {
  
  /**
   * The column list in the json results is used to create a Map for each row in the result.
   */
  type DataPoint = Map[String,Any]
  
  /**
   * An untyped series has a name and a sequence of DataPoints.
   */
  case class Series(name:String, time_precision: Precision, data: List[DataPoint])
  
  /**
   * A TSeries (typed series) has a name, precision and a sequence of objects of type T specified 
   * by the application
   * @see queryAs[T]
   */
  case class TSeries[T](name: String, time_precision:Precision, data: Seq[T])
  
  type QueryResult = Seq[Series]
  type TQueryResult[T] = Seq[TSeries[T]]
  
  def apply(host: String, port: Int, user: String, pwd: String, db:String): InfluxDB = new InfluxDB(host,port, user, pwd, db)
}