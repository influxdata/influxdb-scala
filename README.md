influxdb-scala
==============

Scala client for InfluxDB. 

Unfortunately requires scala 2.11.0-M5 since I could not get the 
[Mappable macro](http://blog.echo.sh/post/65955606729/exploring-scala-macros-map-to-case-class-conversion "Exploring Scala Macros: Map to Case Class Conversion by Jonathan Chow")
to work with 2.10.3 and macro paradise.

Example untyped querying
------------------------

Assuming a test database with a series named data (created with [this](http://obfuscurity.com/2013/11/My-Impressions-of-InfluxDB "obfuscurity blog")), and the following code:

    val db = InfluxDB("localhost",8086,"user","pwd","dbname")  
  
    val result = db.query("select foo,bar from data order asc limit 10", MILLIS)
  
    val data = Await.result(result, 10 seconds)
    for {
      series <- data
      point  <- series.data
    } yield println(s"${series.name} has point $point with time precision ${series.time_precision}")

this will print the following

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

Example typed querying
----------------------
