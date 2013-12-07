import java.util.concurrent.Executors
import scala.concurrent.Await
import org.influxdb.scala.InfluxDB
import scala.concurrent.duration.DurationInt
import org.influxdb.scala.MILLIS

object TestApp extends App {

  import org.influxdb.scala.InfluxDB
  import scala.concurrent.Await
  import scala.concurrent.duration._

  val db = InfluxDB("localhost",8086,"frank","frank","testing")  
  
  val result = db.query("select foo,bar from data", MILLIS)
  
  val data = Await.result(result, 10 seconds)
  for {
    series <- data
    point <- series.data
  } yield println(s"${series.name} has point $point with time precision ${series.time_precision}")
  
  System.exit(0)
}