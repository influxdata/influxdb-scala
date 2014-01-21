package org.influxdb.scala
import org.influxdb.scala.macros.Macros.Mappable
import org.influxdb.scala.macros.Macros.Mappable._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait InfluxDBTypedAPI {
  this: InfluxDBUntypedAPI =>
    
  // implicit conversions from typed to map-based series and vice-versa
  implicit def toDataPoint[T: Mappable](p: T) = implicitly[Mappable[T]].toMap(p)
  implicit def fromDataPoint[T: Mappable](p: DataPoint) = implicitly[Mappable[T]].fromMap(p)
  implicit def TSeriesToSeries[T: Mappable](ts: TSeries[T]): Series = Series(ts.name, ts.time_precision, ts.data.map(toDataPoint(_)))
  implicit def SeriesToTSeries[T: Mappable](s: Series): TSeries[T] = TSeries[T](s.name, s.time_precision, s.data.map(fromDataPoint(_)))
  implicit def QResultToTQResult[T: Mappable](qr: QueryResult): TQueryResult[T] = qr map (SeriesToTSeries(_))
  implicit def futQR2futTQR[T: Mappable](fqr: Future[QueryResult]): Future[TQueryResult[T]] = fqr map (QResultToTQResult(_))

  // the magic of implicit conversions makes this a one-liner
  def queryAs[T: Mappable](queryString: String, precision: Precision): Future[TQueryResult[T]] = query(queryString, precision)

  def insertDataFrom[T: Mappable](seriesName: String, point: T, precision: Precision): Future[Unit] = {
    val dp: DataPoint = point
    insertData(seriesName, dp, precision)
  }

  // implicit conversion takes care of the hard stuff
  def insertDataFrom[T: Mappable](series: TSeries[T]): Future[Unit] = insertData(series)


}