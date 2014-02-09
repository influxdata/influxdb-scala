import java.util.Date
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Failure
import org.influxdb.scala._
import scala.util.Success

object TestApp extends App {

  val client = new InfluxDB("localhost", 8086, "root", "root", "testing") with StandaloneConfig

  client.listDatabases onComplete {
    case Success(databases) => databases foreach (db => println(s"Found db ${db.name} with replication factor ${db.replicationFactor}"))
    case Failure(error) => println(s"Could not list databases: $error")
  }

  client.dropSeries("testing") onComplete {
    case error:Throwable => println(s"Could not drop series: $error")
    case _: Success[Unit] => println("Drop series succeeded.")
  }

  //
  // inserting data
  //

  // single point insertion
  client.insertData("testing", Map("time" -> new Date(), "foo" -> 100, "bar" -> 200), MICROS).onComplete {
    case _: Success[Unit] => println("Single-point insert succeeded!!!")
    case Failure(error) => println(s"Oops, point insert failed: $error")
  }

  // multi-point, single series
  val p1 = Map("foo" -> 100, "bar" -> 200)
  val p2 = Map("bar" -> 100, "baz" -> 300)
  val s = Series("testing", MILLIS, List(p1, p2))
  client.insertData(s) onComplete {
    case _: Success[Unit] => println("Series insert succeeded!!!")
    case Failure(error) => println(s"Oops, series insert failed: $error")
  }

  // get the last point in all series
  client.query("select * from testing order asc limit 10", MILLIS) onComplete {
    case Success(result) => for {
      series <- result
      point <- series.data
    } yield println(s"${series.name} has point $point with time precision ${series.time_precision}")
    case Failure(error) => println(s"Got a query error: $error")
  }

  client.deleteData("testing", new Date(0), new Date()) .onFailure {
    case t:Throwable => println(s"Got an exception deleting data: $t")
  }

  // have to do this, otherwise app won't terminate, wait for 1 second for pending tasks to complete
  client.shutdown(1 seconds)
}