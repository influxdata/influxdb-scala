package org.influxdb.scala

import scala.concurrent.Future

object HTTPServiceComponent {

  trait HTTPService {

    def GET(url: String): Future[String]
    def POST(url: String, body: String, contentType: String): Future[Unit]
    def PUT(url: String, body: String, contentType: String): Future[Unit]
    def DELETE(url: String): Future[Unit]
  }
}

// Cake pattern, see http://jonasboner.com/2008/10/06/real-world-scala-dependency-injection-di/
trait HTTPServiceComponent {
  import org.influxdb.scala.HTTPServiceComponent.HTTPService

  val httpService: HTTPService

}