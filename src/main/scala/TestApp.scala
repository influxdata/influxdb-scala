import java.util.concurrent.Executors
import scala.concurrent.Await
import org.influxdb.scala._
import scala.concurrent.duration.DurationInt
import java.util.Date
import scala.concurrent.Await
import scala.concurrent.duration._
  
object TestApp extends App {
    
  val db = InfluxDB("localhost",8086,"frank","frank","testing")  
  
  val result = db.query("select foo,bar from data order asc limit 10", MILLIS)
  
  val data = Await.result(result, 10 seconds)
  for {
    series <- data
    point <- series.data
  } yield println(s"${series.name} has point $point with time precision ${series.time_precision}")

  // now try it with a case class as the type of the resulting data points
  // this requires both of these imports to discover implicit mapping macros
  import org.influxdb.scala.macros.Macros.Mappable
  import org.influxdb.scala.macros.Macros.Mappable._
  
  case class TestPoint(time: Date, sequence_number: BigInt, bar: BigInt, foo: BigInt)

  val typed = db.queryAs[TestPoint]("select foo,bar from data order asc limit 10", MILLIS)
  val tdata = Await.result(typed, 10 seconds)
  for {
    series <- tdata
    point <- series.data
  } yield println(s"${series.name} has point $point with time precision ${series.time_precision}")

  // have to do this, otherwise won't terminate
  db.shutdown
}