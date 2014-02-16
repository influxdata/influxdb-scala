import java.util.Date
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.Some
import scala.util.Failure
import scala.util.Success
import scala.util.Failure
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import scala.Predef.Map.apply
import org.influxdb.scala._
import scala.util.Success

object ExperimentalTestApp extends App {


  val client = new Client("localhost", 8086, "frank", "frank", "testing") with InfluxDBTypedAPI with StandaloneConfig

  import org.influxdb.scala.macros.Macros.Mappable._

  case class TestPoint(time: Date, bar: Option[BigInt], foo: Option[BigInt], baz: Option[BigInt])

  client.queryAs[TestPoint]("select * from testing limit 10", MILLIS) onComplete {
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


  // typed insert single point
  client.insertDataFrom[TestPoint]("testing", TestPoint(new Date(), Some(1), None, Some(2)), MILLIS).onComplete {
    case _: Success[Unit] => println("Typed single point insert succeeded!!!")
    case Failure(error) => println(s"Oops, Typed single point insert failed: $error")
  }
  
  // have to do this, otherwise app won't terminate, wait for 1 second for pending tasks to complete
  client.shutdown(1 seconds)
}