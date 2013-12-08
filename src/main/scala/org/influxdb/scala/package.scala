package org.influxdb

import scala.language.experimental.macros
import java.net.URLEncoder
import org.influxdb.scala.macros.Macros.Mappable

package object scala {
  trait Enum[A] {
    trait Value { self: A =>
      _values :+= this
    }
    private var _values = List.empty[A]
    def values = _values
  }
  
  implicit class StringOps(val s: String) extends AnyVal {
    
    def urlEncoded = URLEncoder.encode(s,"utf-8")
    
  }

  def materialize[T: Mappable](map: Map[String, Any]) = implicitly[Mappable[T]].fromMap(map)

}