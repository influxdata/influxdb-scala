import org.influxdb.scala._
import java.util.Date
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object TestApp extends App {
    
  val db = InfluxDB("localhost",8086,"frank","frank","testing")  
  
  db.query("select foo,bar from data order asc limit 10", MILLIS) onSuccess{ case result =>
    for {
      series <- result
      point <- series.data
    } yield println(s"${series.name} has point $point with time precision ${series.time_precision}")
  }
  
  // now try it with a case class as the type of the resulting data points
  // this requires both of these imports to discover implicit mapping macros
  import org.influxdb.scala.macros.Macros.Mappable
  import org.influxdb.scala.macros.Macros.Mappable._
  
  case class TestPoint(time: Date, sequence_number: BigInt, bar: BigInt, foo: BigInt)

  db.queryAs[TestPoint]("select foo,bar from data order asc limit 10", MILLIS) onSuccess{ case result =>
	  for {
	    series <- result
	    point <- series.data
	  } yield println(s"${series.name} has point $point with time precision ${series.time_precision}")
  }
  
  // have to do this, otherwise won't terminate, wait for 10 seconds for pending tasks to complete
  db.shutdown(10 seconds)
}