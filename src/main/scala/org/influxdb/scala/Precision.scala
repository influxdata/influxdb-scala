package org.influxdb.scala
import java.util.Date

/**
 * "enum" representing time precision for influxb queries and results.
 * one of SECONDS, MILLIS or MICROS. See Enum[T] in package object for the
 * base trait. This particular Enum pattern allows for behavior to be specified
 * on the values. This is used for date-to-int conversions and query construction
 * in this case.
 */
object Precision extends Enum[Precision]

/**
 * Precision values can convert between Date and BigInt and know
 * how to represent themselves in influxdb queries
 */
sealed trait Precision extends Precision.Value {
  def toDate(time: BigInt): Date
  def toBigInt(d: Date): BigInt
  def qs: String
}

case object SECONDS extends Precision {
  override val qs = "s"
  override def toDate(time: BigInt): Date = new Date(time.longValue * 1000)
  override def toBigInt(d: Date): BigInt = d.getTime() / 1000
}

case object MILLIS extends Precision {
  override val qs = "m"
  override def toDate(time: BigInt): Date = new Date(time.longValue)
  override def toBigInt(d: Date): BigInt = d.getTime()
}

case object MICROS extends Precision {
  override val qs = "u"
  override def toDate(time: BigInt): Date = new Date(time.longValue / 1000)
  override def toBigInt(d: Date): BigInt = d.getTime() * 1000
}