package org.influxdb.scala

import scala.concurrent.Future

// Cake pattern, see http://jonasboner.com/2008/10/06/real-world-scala-dependency-injection-di/
abstract trait HTTPServiceComponent {

  val jsonContentType: String = "application/json"

  val httpService: HTTPService // abstract; implementations must provide a value

  trait HTTPService {

    def GET(url: String): Future[String]
    def POST(url: String, body: String, contentType: String): Future[Unit]
    def PUT(url: String, body: String, contentType: String): Future[Unit]
    def DELETE(url: String): Future[Unit]
  }
}