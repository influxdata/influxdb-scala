package org.influxdb.scala

import java.util.concurrent.ExecutorService
import scala.concurrent.Future

object HTTPServiceComponent {

  trait HTTPService {
	
    def GET(url:String)(implicit pool:ExecutorService):Future[String]
	def POST(url:String, body:String, contentType:String)(implicit pool: ExecutorService):Future[Unit]
  }  
}

// Cake pattern, see http://jonasboner.com/2008/10/06/real-world-scala-dependency-injection-di/
trait HTTPServiceComponent {
  import org.influxdb.scala.HTTPServiceComponent.HTTPService

  val httpService : HTTPService
  
  
}