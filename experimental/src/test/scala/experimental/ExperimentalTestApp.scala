import java.util.Date
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success
import scala.util.Failure
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import scala.Predef.Map.apply
import org.influxdb.scala.Series
import org.influxdb.scala.HTTPServiceComponent
import org.influxdb.scala.MILLIS
import org.influxdb.scala.MICROS
import org.influxdb.scala.InfluxDB
import org.influxdb.scala.JsonConverterComponent
import org.influxdb.scala.AsyncHttpClientImpl
import org.influxdb.scala.Json4sJsonConverter
import org.influxdb.scala.InfluxDBTypedAPI

object ExperimentalTestApp extends App {


  object db extends InfluxDB with HTTPServiceComponent with JsonConverterComponent {
    implicit val pool = Executors.newSingleThreadExecutor()
    override val client = new Client("localhost", 8086, "frank", "frank", "testing") with InfluxDBTypedAPI
    override val httpService = new AsyncHttpClientImpl
    override val jsonConverter = new Json4sJsonConverter
    
    // this is defined here since it is purely for the executorService created here
    def shutdown(timeout: Duration) {
      pool.awaitTermination(timeout.length, timeout.unit)
      pool.shutdown()  
    }
  }

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


  // typed insert single point
  db.client.insertDataFrom[TestPoint]("testing", TestPoint(new Date(), Some(1), None, Some(2)), MILLIS).onComplete {
    case _: Success[Unit] => println("Typed single point insert succeeded!!!")
    case Failure(error) => println(s"Oops, Typed single point insert failed: $error")
  }
  
  // have to do this, otherwise app won't terminate, wait for 1 second for pending tasks to complete
  db.shutdown(1 seconds)
}