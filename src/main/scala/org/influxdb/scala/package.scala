package org.influxdb

import java.net.URLEncoder

package object scala {
  trait Enum[A] {
    trait Value { self: A =>
      _values :+= this
    }
    private var _values = List.empty[A]
    def values = _values
  }
  
  // String extensions
  implicit class StringOps(val s: String) extends AnyVal {
    
    def urlEncoded = URLEncoder.encode(s,"utf-8")
    
  }

}