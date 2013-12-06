import java.util.concurrent.Executors
import org.influxdb.scala.InfluxDB
import org.influxdb.scala.InfluxDB.TimeUnit
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

object TestApp extends App {

  import org.influxdb.scala.InfluxDB
  
  import scala.concurrent._
  import scala.concurrent.duration._
  import org.influxdb.scala.InfluxDB
  import org.influxdb.scala.InfluxDB.TimeUnit._
  import java.util.concurrent.{Executors,ExecutorService}
  implicit lazy val pool = Executors.newFixedThreadPool(2)

  val db = InfluxDB("localhost",8086,"frank","frank","testing")  
  
  val result = db.query("select foo,bar from data", u)
  
  val data = Await.result(result, 10 seconds)
  for {
    series <- data
    point <- series.data
  } yield println(s"${series.name} has point $point with time precision ${series.time_precision}")
  
  System.exit(0)
}