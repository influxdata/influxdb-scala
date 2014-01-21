package org.influxdb.scala

import org.influxdb.scala.JsonConverterComponent.JsonConverter
import scala.util.Try
import scala.annotation.tailrec
import org.json4s.native.JsonParser
import org.json4s.JsonAST._
import org.json4s.DefaultFormats
import org.json4s.native.Serialization.{write => swrite}
import scala.util.Success
import scala.util.Failure
import java.util.Date
import org.json4s.jvalue2monadic
import org.json4s.native.Serialization.{write => swrite}
import scala.math.BigInt.int2bigInt
import scala.math.BigInt.long2bigInt

class Json4sJsonConverter extends JsonConverter {
   
  implicit lazy val formats = DefaultFormats

  // convert a json response to an instance of QueryResult. May fail, hence the Try
  def jsonToSeries(response: String, precision: Precision): Try[QueryResult] = {
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
    
    def seriesToJson(s:Series):String = {
      val columns:List[String] = allColumns(s.data)
      val points = s.data.map(point => collectValues(columns, point, s.time_precision))
      val series = JObject(
        "name" -> JString(s.name),
        "columns" -> JArray(columns.map(c => JString(c))),
        "points" -> JArray(points))
      swrite(List(series))    
    }
    
   /**
     * extracts the value for key c from a Map[String,Any] as a JValue of the appropriate type.
     * If key is not found returns JNull. 
     * Handles values of type T and Option[T] where T in {Int,Double,Float,BigDecimal,BigInt,String,Boolean,Date}
     * TODO this may benefit from scalavro's union types to make this more typed
     * @param point the data point
     * @param c column name
     * @precision time precision for values of type Date
     */
    private def pointValue(point: DataPoint, c: String, precision: Precision):JValue = {
      def jValueOf(x:Any):JValue = x match {
        case i: Int => JInt(i)
        case d: Double => JDouble(d)
        case f: Float => JDouble(f)
        case d: BigDecimal => JDecimal(d)
        case i: BigInt => JInt(i)
        case l: Long => JInt(l)
        case s: String => JString(s)
        case b: Boolean => JBool(b)
        case d: Date => JInt(precision.toBigInt(d))
      }
      point.getOrElse(c, null) match {
        case None => JNull
        case Some(thing) => jValueOf(thing)
        case null => JNull
        case x@_ => jValueOf(x)
      }
    }
    
    /**
     * transforms a DataPoint (Map[String,Any]) to a JArray of values. Size of result will be equal to size
     * of columns with null values for missing keys
     */
    private def collectValues(columns: List[String], point: DataPoint, precision: Precision): JArray = {
      val values = columns.map(c => pointValue(point, c, precision))
      JArray(values)
    }
}