package org.influxdb.scala
import java.util.Date

object Precision extends Enum[Precision]
  
  sealed trait Precision extends Precision.Value {
    def toDate(time: BigInt): Date
    def qs:String
  }
  case object SECONDS extends Precision {
    val qs="s"
	def toDate(time: BigInt): Date = {
	    new Date(time.longValue * 1000)
	} 
  }
  case object MILLIS extends Precision {
    val qs="m"
    def toDate(time: BigInt): Date = {
	    new Date(time.longValue)
	} 
  }
  case object MICROS extends Precision {
    val qs="u"
    def toDate(time: BigInt): Date = {
	    new Date(time.longValue / 1000)
	} 
  }