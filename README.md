influxdb-scala
==============

This is the Scala client for [InfluxDB](http://influxdb.org). 

The core and standalone modules are compatible with scala 2.10.3 (and possible older versions). However, the typed API available in the experimental sub module
requires a scala 2.11.0 milestone build (currently built with M7) since I could not get the 
[Mappable macro](http://blog.echo.sh/post/65955606729/exploring-scala-macros-map-to-case-class-conversion "Exploring Scala Macros: Map to Case Class Conversion by Jonathan Chow")
to work with 2.10.3 and the macro paradise plugin.

###Dependencies

The standalone version uses [async-httpclient](https://github.com/AsyncHttpClient/async-http-client) to communicate with the InfluxDB REST
service and [json4s](http://json4s.org) to convert to and from json.

These dependencies are injected using the [Cake Pattern](http://jonasboner.com/2008/10/06/real-world-scala-dependency-injection-di/).
In order to configure an influxdb client that uses the async-httpclient and json4s json converters, you can write the following:

    import org.influxdb.scala._
    
    val client = new Client("localhost", 8086, "frank", "frank", "testing") with StandaloneConfig
    
The StandAloneConfig trait is an empty trait that combines the AsyncHttpClientComponent with the Json4sJsonConverterComponent
When running in a Play! framework environment, one could write a PlayConfig combining WS and the json macros and use that instead.
Also, the (to be written) unit tests will use a MockConfig in order to cut out the actual database altogether.

###Untyped querying

Assuming the above db object and a test database with a series named testing (created with [this](http://obfuscurity.com/2013/11/My-Impressions-of-InfluxDB "obfuscurity blog")), and the following code:
  
    client.query("select foo,bar from testing order asc limit 10", MILLIS) onSuccess{ case result =>
      for {
        series <- result
        point <- series.data
      } yield println(s"${series.name} has point $point with time precision ${series.time_precision}")
    }
    
Note that the query method is asynchronous and returns a **Future[QueryResult]**. 
A **QueryResult** is a sequence of **Series** objects since a query can return results form multiple series.
I used a for comprehension to emphasize that the result can contain multiple series, even though in this case there is only one. 
A **Series** has a name, the given time precision of the query and a sequence of data points. In the untyped case, a data point
is simply a **Map[String,Any]** (aliased with type alias *DataPoint*) mapping column name to value. The above code will print the following output:

    testing has point Map(time -> Mon Dec 02 20:49:45 EST 2013, sequence_number -> 1, bar -> 287, foo -> 214) with time precision MILLIS
    testing has point Map(time -> Mon Dec 02 20:49:46 EST 2013, sequence_number -> 2, bar -> 156, foo -> 246) with time precision MILLIS
    testing has point Map(time -> Mon Dec 02 20:49:47 EST 2013, sequence_number -> 3, bar -> 89, foo -> 236) with time precision MILLIS
    testing has point Map(time -> Mon Dec 02 20:49:48 EST 2013, sequence_number -> 4, bar -> 227, foo -> 179) with time precision MILLIS
    testing has point Map(time -> Mon Dec 02 20:49:49 EST 2013, sequence_number -> 5, bar -> 160, foo -> 250) with time precision MILLIS
    testing has point Map(time -> Mon Dec 02 20:49:50 EST 2013, sequence_number -> 6, bar -> 170, foo -> 172) with time precision MILLIS
    testing has point Map(time -> Mon Dec 02 20:49:51 EST 2013, sequence_number -> 7, bar -> 157, foo -> 211) with time precision MILLIS
    testing has point Map(time -> Mon Dec 02 20:49:52 EST 2013, sequence_number -> 8, bar -> 134, foo -> 237) with time precision MILLIS
    testing has point Map(time -> Mon Dec 02 20:49:53 EST 2013, sequence_number -> 9, bar -> 294, foo -> 188) with time precision MILLIS
    testing has point Map(time -> Mon Dec 02 20:49:54 EST 2013, sequence_number -> 10, bar -> 137, foo -> 106) with time precision MILLIS

    
### Inserting new data

As with querying, insertion can also be done both from **Map[String,Any]** datapoints as well as case classes defined by you.
Inserting a single datapoint using a map is done as follows:

    client.insertData("testing", Map("time" -> new Date(), "foo" -> 100, "bar" -> 200), MICROS).onComplete {
      case _: Success[Unit] => println("Single-point insert succeeded!!!")
      case Failure(error) => println(s"Oops, point insert failed: $error")
    }
    
Note here that insertion is also asynchronous and returns a Future (of Unit in this case - this may need to change in the future). 
Success or failure is indicated by the future completing successfully or with a failure, as demonstrated in the example above.

Multiple data points (indeed an entire series) can be inserted as follows:

    val p1 = Map("foo" -> 100, "bar" -> 200)
    val p2 = Map("bar" -> 100, "baz" -> 300)
    val series = Series("testing", MILLIS, List(p1, p2))
    client.insertData(series) onComplete {
      case _: Success[Unit] => println("Series insert succeeded!!!")
      case Failure(error) => println(s"Oops, series insert failed: $error")
    }   


### Dropping an entire series

    client.dropSeries("testing")

This will drop the series named testing and thus delete all data contained in it.

### Admin

     client.listDatabases onComplete {
        case Success(databases) => databases foreach (db => println(s"Found db ${db.name} with replication factor ${db.replicationFactor}"))
        case Failure(error) => println(s"Could not list databases: $error")
     }

Lists all the databases in the cluster. Requires cluster admin credentials.

###Typed querying (in the experimental module - requires a scala 2.11.0 milestone build!)

Using the same data as above, the API can also deliver the data point results as instances of case classes defined by you! 
To do so, use the *queryAs[T]* method, as follows:

    import org.influxdb.scala.macros.Macros.Mappable
    import org.influxdb.scala.macros.Macros.Mappable._
    
    // simple example case class
    case class TestPoint(time: Date, sequence_number: BigInt, bar: BigInt, foo: BigInt)

    client.queryAs[TestPoint]("select foo,bar from testing order asc limit 10", MILLIS) onSuccess{ case result =>
	  for {
	    series <- result
	    point <- series.data
	  } yield println(s"${series.name} has point $point with time precision ${series.time_precision}")
    }
    
Just as in the untyped case, the queryAs[T] method is *ansynchronous* and in this case returns a **Future[TQueryResult[T]]** with
T being **TestPoint** in this example. TQueryResult is a sequence of **TSeries[T]**, which represents its data points as a
sequence of T. The additional imports are required to discover the implicit macro that does the 
conversion from Map[String,Any] to Testpoint. When you run the above code, the output looks like this:

    testing has point TestPoint(Mon Dec 02 20:49:45 EST 2013,1,287,214) with time precision MILLIS
    testing has point TestPoint(Mon Dec 02 20:49:46 EST 2013,2,156,246) with time precision MILLIS
    testing has point TestPoint(Mon Dec 02 20:49:47 EST 2013,3,89,236) with time precision MILLIS
    testing has point TestPoint(Mon Dec 02 20:49:48 EST 2013,4,227,179) with time precision MILLIS
    testing has point TestPoint(Mon Dec 02 20:49:49 EST 2013,5,160,250) with time precision MILLIS
    testing has point TestPoint(Mon Dec 02 20:49:50 EST 2013,6,170,172) with time precision MILLIS
    testing has point TestPoint(Mon Dec 02 20:49:51 EST 2013,7,157,211) with time precision MILLIS
    testing has point TestPoint(Mon Dec 02 20:49:52 EST 2013,8,134,237) with time precision MILLIS
    testing has point TestPoint(Mon Dec 02 20:49:53 EST 2013,9,294,188) with time precision MILLIS
    testing has point TestPoint(Mon Dec 02 20:49:54 EST 2013,10,137,106) with time precision MILLIS
        
Note that all attributes of cases classes used here must be one of the following types :

* Int
* Double
* Float
* BigDecimal
* BigInt
* String
* Date
* Option[T] where T is one of the above

It should come as no surprise at this point that inserts can also be performed from case class instances. Here is an example:

    val tp = TestPoint(new Date(), Some(1), None, Some(2))
    client.insertDataFrom[TestPoint]("testing", tp, MILLIS).onComplete {
      case _: Success[Unit] => println("Typed single point insert succeeded!!!")
      case Failure(error) => println(s"Oops, Typed single point insert failed: $error")
    }
