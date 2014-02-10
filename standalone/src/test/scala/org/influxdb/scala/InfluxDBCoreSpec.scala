package org.influxdb.scala

import org.scalatest.{Matchers, FlatSpec}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.Date

/**
 * Unit tests for the standalone InfluxDB Scala client (using Json4sJsonConverterComponent).
 * These mock the HTTPService, so they do not actually require a running InfluxDB
 * To keep the tests simple and sequential they use Await.result on the futures returned from the API;
 * Never do this in real application code, keep that non-blocking and asynchronous at all cost
 */
class InfluxDBCoreSpec extends FlatSpec with Matchers {

  val db = new InfluxDB("localhost", 8086, "root", "root", "testing") with MockHttpService with Json4sJsonConverterComponent

  it should "return a list of databases" in {
    val dbsF = db.listDatabases
    val dbs = Await.result(dbsF, 1 second) // DO NOT do this in real code, keep it non-blocking
    dbs.length should be (2)
    dbs(0).name should be ("dbOne")
    dbs(0).replicationFactor should be (1)
    dbs(1).name should be ("dbTwo")
    dbs(1).replicationFactor should be (2)
  }

  it should "return a query result" in {
    val qF = db.query("select * from testing", MILLIS)
    val result = Await.result(qF, 1 second) // DO NOT do this in real code, keep it non-blocking
    val t0 = new Date(100)
    result.length should be (1)
    val series = result(0)
    series.name should be ("testing")
    series.time_precision should be (MILLIS)
    series.data.length should be (3)
    series.data(0)("time") should be (t0)
    series.data(0)("sequence_number") should be (Some(1))
    series.data(0)("bar") should be (Some(200))
    series.data(0)("baz") should be (None)
    series.data(0)("foo") should be (Some(100))
    series.data(2)("baz") should be (Some(300))
  }

  //
  // Now we're seeing the real power of the Cake pattern !
  //

  trait MockHttpService extends HTTPServiceComponent {
    import org.mockito.Mockito._
    override val httpService = mock(classOf[HTTPService])
    private val dblist = "http://localhost:8086/db?u=root&p=root"
    private val query = "http://localhost:8086/db/testing/series?u=root&p=root&time_precision=m&q=select+*+from+testing"

    when(httpService.GET(dblist)).thenReturn(Future[String](
      """
        [
          {"name":"dbOne", "replicationFactor": 1},
          {"name":"dbTwo", "replicationFactor": 2},
        ]
      """
    ))
    when(httpService.GET(query)).thenReturn(Future[String](
      """
        [
          {
            "name":"testing",
            "columns":["time","sequence_number","bar","baz","foo"],
            "points":[
              [100,1,200,null,100],
              [200,2,200,null,100],
              [300,3,100,300,null]
            ]
          }
        ]
      """
    ))
  }
}
