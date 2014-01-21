package org.influxdb.scala

import scala.concurrent.Future
import org.influxdb.scala.HTTPServiceComponent.HTTPService

trait InfluxDBAdminAPI {

  self: HTTPService =>
  def listDatabases: Future[List[String]]
}