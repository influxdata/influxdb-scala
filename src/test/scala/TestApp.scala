import java.util.Date
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success
import scala.util.Failure
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import scala.Predef.Map.apply
import org.influxdb.scala.Series
import org.influxdb.scala.InfluxDBClientComponent
import org.influxdb.scala.HTTPServiceComponent
import org.influxdb.scala.MILLIS
import org.influxdb.scala.MICROS
import org.influxdb.scala.AsyncHttpClientImpl

object TestApp extends App {


  /**
   * This is the cake pattern wiring where we choose to use the AsyncHttpClient version
   * of the abstract HTTPService. In the context of a Play! framework app, you
   * can replace AsyncHttpClientImpl with a different implementation trait that uses WS.
   * See http://jonasboner.com/2008/10/06/real-world-scala-dependency-injection-di/
   */
  object db extends InfluxDBClientComponent with HTTPServiceComponent {
    implicit val pool = Executors.newSingleThreadExecutor()
    override val client = new InfluxDBClient("localhost", 8086, "frank", "frank", "testing")
    override val httpService = new AsyncHttpClientImpl
    
    // this is defined here since it is purely for the executorService created here
    def shutdown(timeout: Duration) {
      pool.awaitTermination(timeout.length, timeout.unit)
      pool.shutdown()  
    }
  }

  // get the last point in all series
  db.client.query("select * from data order asc limit 10", MILLIS) onComplete {
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

  case class TestPoint(time: Date, bar: Option[BigInt], foo: Option[BigInt], baz: Option[BigInt])

  db.client.queryAs[TestPoint]("select * from testing limit 10", MILLIS) onComplete {
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
  db.client.insertData("testing", Map("time" -> new Date(), "foo" -> 100, "bar" -> 200), MICROS).onComplete {
    case _: Success[Unit] => println("Single-point insert succeeded!!!")
    case Failure(error) => println(s"Oops, point insert failed: $error")
  }

  // multi-point, single series
  val p1 = Map("foo" -> 100, "bar" -> 200)
  val p2 = Map("bar" -> 100, "baz" -> 300)
  val s = Series("testing", MILLIS, List(p1, p2))
  db.client.insertData(s) onComplete {
    case _: Success[Unit] => println("Series insert succeeded!!!")
    case Failure(error) => println(s"Oops, series insert failed: $error")
  }

  // typed insert single point
  db.client.insertDataFrom[TestPoint]("testing", TestPoint(new Date(), Some(1), None, Some(2)), MILLIS).onComplete {
    case _: Success[Unit] => println("Typed single point insert succeeded!!!")
    case Failure(error) => println(s"Oops, Typed single point insert failed: $error")
  }
  
  // have to do this, otherwise app won't terminate, wait for 1 second for pending tasks to complete
  db.shutdown(1 seconds)
}