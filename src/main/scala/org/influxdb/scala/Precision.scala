package org.influxdb.scala
import java.util.Date

object Precision extends Enum[Precision]
  
  sealed trait Precision extends Precision.Value {
    def toDate(time: BigInt): Date
    def toBigInt(d: Date): BigInt
    def qs:String
  }
  case object SECONDS extends Precision {
    override val qs="s"
	override def toDate(time: BigInt): Date = {
	    new Date(time.longValue * 1000)
	} 
    override def toBigInt(d: Date): BigInt = d.getTime() / 1000
  }
  case object MILLIS extends Precision {
    override val qs="m"
    override def toDate(time: BigInt): Date = {
	    new Date(time.longValue)
	} 
    override def toBigInt(d: Date): BigInt = d.getTime()
  }
  case object MICROS extends Precision {
    override val qs="u"
    override def toDate(time: BigInt): Date = {
	    new Date(time.longValue / 1000)
	}
    override def toBigInt(d: Date): BigInt = d.getTime() * 1000
  }