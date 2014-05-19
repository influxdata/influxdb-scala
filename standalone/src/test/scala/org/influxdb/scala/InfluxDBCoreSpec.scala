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

  "db.listDatabases" should "return a list of databases" in {
    val dbsF = db.listDatabases
    val dbs = Await.result(dbsF, 1 second) // DO NOT do this in real code, keep it non-blocking
    dbs.length should be (2)
    dbs(0).name should be ("dbOne")
    dbs(0).replicationFactor should be (1)
    dbs(1).name should be ("dbTwo")
    dbs(1).replicationFactor should be (2)
  }

  "db.query" should "return a query result" in {
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

  "db.listContinuousQueries" should "return 1 query" in {
    val f = db.listContinuousQueries
    val result = Await.result(f, 1 second)
    result.length should be (1)
    result(0).id should be (1)
    result(0).query should be ("select * from testing group by time(1h) into testing.1h")
  }

  "db.listShards" should "return all shards" in {
    val f = db.listShards
    val result = Await.result(f, 1 second)
    result.length should be (5)
    result.sortBy(_.id) should equal (List(
      Shard(1, true, List(1), new Date(1400648400000L), new Date(1400652000000L)),
      Shard(2, true, List(1), new Date(1400652000000L), new Date(1400655600000L)),
      Shard(3, true, List(1), new Date(1400655600000L), new Date(1400659200000L)),
      Shard(4, false, List(1,2), new Date(1400486400000L), new Date(1400572800000L)),
      Shard(5, false, List(1), new Date(1400572800000L), new Date(1400659200000L))
    ))
  }

  //
  // Now we're seeing the real power of the Cake pattern !
  //

  trait MockHttpService extends HTTPServiceComponent {
    import org.mockito.Mockito._
    override val httpService = mock(classOf[HTTPService])
    private val dblist = "http://localhost:8086/db?u=root&p=root"
    private val query = "http://localhost:8086/db/testing/series?u=root&p=root&time_precision=m&q=select+*+from+testing"
    private val continuousQueries = "http://localhost:8086/db/testing/continuous_queries"
    private val shards = "http://localhost:8086/cluster/shards?u=root&p=root"

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
    when(httpService.GET(continuousQueries)).thenReturn(Future[String] (
      // I'm sorry, but this is some hideous json...
      """
        [
            {
                "points": [
                    {
                        "values": [
                            {
                                "int64_value": 1
                            },
                            {
                                "string_value": "select * from testing group by time(1h) into testing.1h"
                            }
                        ],
                        "timestamp": 1392575228,
                        "sequence_number": 1
                    }
                ],
                "name": "continuous queries",
                "fields": [
                    "id",
                    "query"
                ]
            }
        ]
      """
    ))
    when(httpService.GET(shards)).thenReturn(Future[String] (
      """
        {
          "longTerm": [
            {"endTime":1400659200,"id":5,"serverIds":[1],"startTime":1400572800},
            {"endTime":1400572800,"id":4,"serverIds":[1,2],"startTime":1400486400},
          ],
          "shortTerm": [
            {"endTime":1400659200,"id":3,"serverIds":[1],"startTime":1400655600},
            {"endTime":1400655600,"id":2,"serverIds":[1],"startTime":1400652000},
            {"endTime":1400652000,"id":1,"serverIds":[1],"startTime":1400648400}
          ]
        }
      """
    ))
  }
}
