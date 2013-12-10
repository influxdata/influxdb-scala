import org.influxdb.scala._
import java.util.Date
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success
import scala.util.Failure
import org.influxdb.scala.InfluxDB.Series

object TestApp extends App {
    
  val db = InfluxDB("localhost",8086,"frank","frank","testing")  
  
  // get the last point in all series
  db.query("select * from data order asc limit 10", MILLIS) onComplete { 
    case Success(result) => for {
      series <- result
      point <- series.data
    } yield println(s"${series.name} has point $point with time precision ${series.time_precision}")
    case Failure(error) => println(s"Got a query error: $error")
  }
  
  // now try it with a case class as the type of the resulting data points
  // this requires both of these imports to discover implicit mapping macros
  import org.influxdb.scala.macros.Macros.Mappable
  import org.influxdb.scala.macros.Macros.Mappable._
  
  case class TestPoint(time: Date, bar: BigInt, foo: BigInt)


  db.queryAs[TestPoint]("select * from testing limit 10", MILLIS) onComplete{ 
    case Success(result) =>
	  for {
	    series <- result
	    point <- series.data
	  } yield println(s"${series.name} has point $point with time precision ${series.time_precision}")
    case Failure(error) => println(s"Typed query error: $error")
  }
 
  //
  // inserting data
  //
  
  // single point insertion
  db.insertData("testing", Map("time"->new Date(), "foo" -> 100, "bar"->200), MICROS).onComplete {
    case _:Success[Unit] => println("Single-point insert succeeded!!!")
    case Failure(error) => println(s"Oops, point insert failed: $error")
  }
  
  // multi-point, single series
  val p1 = Map("foo" -> 100, "bar"->200)
  val p2 = Map("bar" -> 100, "baz"->300)
  val s = Series("testing", MILLIS, List(p1,p2))
  db.insertData(s) onComplete {
    case _:Success[Unit] => println("Series insert succeeded!!!")
    case Failure(error) => println(s"Oops, series insert failed: $error")
    
  }
    
  // have to do this, otherwise won't terminate, wait for 10 seconds for pending tasks to complete
  db.shutdown(3 seconds)
}