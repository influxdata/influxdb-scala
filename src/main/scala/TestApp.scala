object TestApp extends App {

  import org.influxdb.scala.InfluxDB
  
  import scala.concurrent._
  import scala.concurrent.duration._
  import org.influxdb.scala.InfluxDB
  import org.influxdb.scala.InfluxDB.TimeUnit
  import java.util.concurrent.{Executors,ExecutorService}
  implicit lazy val pool = Executors.newFixedThreadPool(2)

  val db = InfluxDB("localhost",8086,"frank","frank","testing")  
  
  val result = db.query("select foo,bar from data", TimeUnit.u)
  
  println(s"result = ${Await.result(result, 10 seconds)}")
  
  System.exit(0)
}