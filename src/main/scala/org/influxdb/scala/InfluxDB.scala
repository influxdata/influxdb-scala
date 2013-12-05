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
import scala.util.Success
import java.net.URLEncoder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.annotation.tailrec

class InfluxDB(hostName: String, port: Int, user: String, pwd: String, db:String) {
  
  implicit lazy val formats = DefaultFormats

  val LOG = LoggerFactory.getLogger("InfluxDB")
  
  /**
   * Execute the query asynchronously, resulting in a future Series
   */
  def query(queryString:String, precision: TimeUnit.TimeUnit)(implicit exec: Executor): Future[QueryResult] = {
    val client = new AsyncHttpClient()
    val encodedQ = URLEncoder.encode(queryString,"utf-8")
    val url = s"http://$hostName:$port/db/$db/series?u=$user&p=$pwd&time_precision=$precision&q=$encodedQ"
    LOG.debug(s"url = $url")
    val f = client.prepareGet(url).execute()
    val p = Promise[QueryResult]()
    f.addListener(new Runnable() {
      def run = {
    	val response = f.get
    	if (response.getStatusCode() < 400) {
    	  p.complete(jsonToSeries(response.getResponseBody()))
    	} else {
    	  p.failure(
    	      new RuntimeException(s"Error response: ${response.getStatusCode()}: ${response.getResponseBody()}"))
    	}
      }
    }, exec)
    p.future
   }
   
   def insertData(series:Series):Future[Try[Unit]] = {
???     
   }
   
   private def jsonToSeries(response: String): Try[QueryResult] = {
      LOG.debug(s"received: $response")
      val json = JsonParser.parse(response)
      val data = json.extract[QueryResult]
      
      @tailrec
	  def rmap[A,B](in: Seq[(A,B)], out: Map[A,B] = Map[A,B]() ): Map[A,B] = in match {
	     case (e @ (k,v)) :: tail if out.getOrElse(k,v) == v => rmap(tail, out + e)
	     case Nil => out
	     //case _ => None
	  }
      
      def constructSeries(name:String, cols: Seq[String], points: List[JValue]) = {
        // convert JArray to List
        val ps = for {JArray(point) <-points} yield point
        // create List[Seq[(colname -> value)]] 
        val r = ps.map (p => cols.zipWithIndex.map {case (col, index) => (col -> p(index)) })
        
        def extractValue(v : JValue):Any = {
          v match {
            case JInt(anInt) => anInt
            case JString(aString) => aString
            case JBool(aBool) => aBool
            case _ => None
          }
        }
        
        val maps = r map (e => rmap[String,Any](e map (l => (l._1, extractValue(l._2)))) )
        Series(name, maps)
      }
      
      val result = for {
        JObject(seriesData) <- json
        JField("name",JString(name)) <- seriesData
        JField("columns", JArray(cols)) <- seriesData
        JField("points", JArray(points)) <- seriesData
        
      } yield constructSeries(name, for {JString(s) <- cols} yield s, points)
      Success(result)
   }
}

object InfluxDB {

  /**
   * Granularity of time units in data points
   */
  object TimeUnit extends Enumeration {
    type TimeUnit = Value
    val s, m, u = Value
  }
  
  /**
   * A point in time is a long with a given granularity representing an 
   * epoch from 1, Jan 1970 in either seconds, milliseconds, or microseconds.
   */
  type Time = (Long,TimeUnit.TimeUnit)
  
  /**
   * columns is just a sequence of strings
   */
  type Columns = Seq[String] 
  
  type DataPoint = Map[String,Any]
  
  /**
   * A series has a name, a list of columns and data.
   * The length of columnData in each data has to match the length of columns
   */
  case class Series(name:String,data:Seq[DataPoint])
  
  type QueryResult = Seq[Series]
  
  def apply(host: String, port: Int, user: String, pwd: String, db:String): InfluxDB = 
    new InfluxDB(host,port, user, pwd, db)
}