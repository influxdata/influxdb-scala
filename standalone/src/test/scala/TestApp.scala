import java.util.Date
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Failure
import org.influxdb.scala._
import scala.util.Success

object TestApp extends App {

  val client = new Client("localhost", 8086, "frank", "frank", "testing") with StandaloneConfig

  // get the last point in all series
  client.query("select * from testing order asc limit 10", MILLIS) onComplete {
    case Success(result) => for {
      series <- result
      point <- series.data
    } yield println(s"${series.name} has point $point with time precision ${series.time_precision}")
    case Failure(error) => println(s"Got a query error: $error")
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

  // have to do this, otherwise app won't terminate, wait for 1 second for pending tasks to complete
  client.shutdown(1 seconds)
}