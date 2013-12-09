influxdb-scala
==============

This is Scala client for [InfluxDB](http://influxdb.org). 

Unfortunately requires scala 2.11.0-M5 since I could not get the 
[Mappable macro](http://blog.echo.sh/post/65955606729/exploring-scala-macros-map-to-case-class-conversion "Exploring Scala Macros: Map to Case Class Conversion by Jonathan Chow")
to work with 2.10.3 and macro paradise.

###Dependencies

Currently uses [async-httpclient](https://github.com/AsyncHttpClient/async-http-client) to communicate with the InfluxDB REST
service and [json4s](http://json4s.org) to convert to and from json.
I'm looking for a way to abstract these aspects so other libraries can be used, for example in a Play! framework application
it would make more sense to use WS and the existing json macros.

###Untyped querying

Assuming a test database with a series named data (created with [this](http://obfuscurity.com/2013/11/My-Impressions-of-InfluxDB "obfuscurity blog")), and the following code:

    val db = InfluxDB("localhost",8086,"user","pwd","dbname")  
  
    db.query("select foo,bar from data order asc limit 10", MILLIS) onSuccess{ case result =>
      for {
        series <- result
        point <- series.data
      } yield println(s"${series.name} has point $point with time precision ${series.time_precision}")
    }
    
Note that the query method is asynchronous and returns a *Future[QueryResult]*. I used a for comprehension
as an example of how to handle multiple series in the result, even though in this case there is only one.
A QueryResults is a sequence of Series objects since a query can return results form multiple Series. A Series
has a name, the given time precision of the query and a sequence of data points. In the untyped case, a data point
is simply a Map[String,Any] mapping column name to value. The above code will print the following output:

    data has point Map(time -> Mon Dec 02 20:49:45 EST 2013, sequence_number -> 1, bar -> 287, foo -> 214) with time precision MILLIS
    data has point Map(time -> Mon Dec 02 20:49:46 EST 2013, sequence_number -> 2, bar -> 156, foo -> 246) with time precision MILLIS
    data has point Map(time -> Mon Dec 02 20:49:47 EST 2013, sequence_number -> 3, bar -> 89, foo -> 236) with time precision MILLIS
    data has point Map(time -> Mon Dec 02 20:49:48 EST 2013, sequence_number -> 4, bar -> 227, foo -> 179) with time precision MILLIS
    data has point Map(time -> Mon Dec 02 20:49:49 EST 2013, sequence_number -> 5, bar -> 160, foo -> 250) with time precision MILLIS
    data has point Map(time -> Mon Dec 02 20:49:50 EST 2013, sequence_number -> 6, bar -> 170, foo -> 172) with time precision MILLIS
    data has point Map(time -> Mon Dec 02 20:49:51 EST 2013, sequence_number -> 7, bar -> 157, foo -> 211) with time precision MILLIS
    data has point Map(time -> Mon Dec 02 20:49:52 EST 2013, sequence_number -> 8, bar -> 134, foo -> 237) with time precision MILLIS
    data has point Map(time -> Mon Dec 02 20:49:53 EST 2013, sequence_number -> 9, bar -> 294, foo -> 188) with time precision MILLIS
    data has point Map(time -> Mon Dec 02 20:49:54 EST 2013, sequence_number -> 10, bar -> 137, foo -> 106) with time precision MILLIS

###Typed querying

Using the same data as above, the API can also deliver the data point results as instances of a case class using the *queryAs[T]* method, as follows:

    import org.influxdb.scala.macros.Macros.Mappable
    import org.influxdb.scala.macros.Macros.Mappable._
  
    case class TestPoint(time: Date, sequence_number: BigInt, bar: BigInt, foo: BigInt)

    db.queryAs[TestPoint]("select foo,bar from data order asc limit 10", MILLIS) onSuccess{ case result =>
	  for {
	    series <- result
	    point <- series.data
	  } yield println(s"${series.name} has point $point with time precision ${series.time_precision}")
    }
    
As in the untyped case, queryAs[T] is ansynchronous and in this case returns a *Future[TQueryResult[T]]* with
T being *TestPoint* in this example. TQueryResult is a sequence of *TSeries[T]*, which represents its data points as a
sequence of T. The additional imports are required to discover the implicit macro that does the 
conversion from Map[String,Any] to Testpoint. When you run this, the output looks like this:

    data has point TestPoint(Mon Dec 02 20:49:45 EST 2013,1,287,214) with time precision MILLIS
    data has point TestPoint(Mon Dec 02 20:49:46 EST 2013,2,156,246) with time precision MILLIS
    data has point TestPoint(Mon Dec 02 20:49:47 EST 2013,3,89,236) with time precision MILLIS
    data has point TestPoint(Mon Dec 02 20:49:48 EST 2013,4,227,179) with time precision MILLIS
    data has point TestPoint(Mon Dec 02 20:49:49 EST 2013,5,160,250) with time precision MILLIS
    data has point TestPoint(Mon Dec 02 20:49:50 EST 2013,6,170,172) with time precision MILLIS
    data has point TestPoint(Mon Dec 02 20:49:51 EST 2013,7,157,211) with time precision MILLIS
    data has point TestPoint(Mon Dec 02 20:49:52 EST 2013,8,134,237) with time precision MILLIS
    data has point TestPoint(Mon Dec 02 20:49:53 EST 2013,9,294,188) with time precision MILLIS
    data has point TestPoint(Mon Dec 02 20:49:54 EST 2013,10,137,106) with time precision MILLIS
    
Always make sure you invoke shutdown on the db when you no longer need it, this will terminate
the background threads that allow this client to be asynchronous.

    db.shutdown(10 seconds)
    
this will block for at most 10 seconds to allow pending queries to complete.

### Inserting new data
