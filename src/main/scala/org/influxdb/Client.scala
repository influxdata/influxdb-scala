package org.influxdb

import org.json4s.jackson.Serialization
import org.json4s.NoTypeHints
import com.ning.http.client.{Response, AsyncHttpClient}
import java.util.concurrent.{Future, TimeUnit}
import org.json4s.jackson.Serialization._
import scala.Some
import java.net.URLEncoder


class Client(host: String = "localhost:8086", var username: String = "root", var password: String = "root", var database: String = "", schema: String = "http") {
  implicit val formats = Serialization.formats(NoTypeHints)
  private val httpClient = new AsyncHttpClient()

  var (timeout, unit) = (3, TimeUnit.SECONDS)

  def close() {
    httpClient.close()
  }

  def ping: error.Error = {
    try {
      val url = getUrl("/ping")
      responseToError(getResponse(httpClient.prepareGet(url).execute()))
    } catch {
      case ex: Exception => Some(ex.getMessage)
    }
  }

  def createDatabase(name: String, replicationFactor: Int = 1): error.Error = {
    try {
      val url = getUrl("/db")
      val payload = write(Map("name" -> name, "replicationFactor" -> replicationFactor))

      val fr = httpClient.preparePost(url).addHeader("Content-Type", "application/json").setBody(payload).execute()
      responseToError(getResponse(fr))
    } catch {
      case ex: Exception => Some(ex.getMessage)
    }
  }

  def deleteDatabase(name: String): error.Error = {
    try {
      val url = getUrl(s"/db/$name")
      val fr = httpClient.prepareDelete(url).execute()
      responseToError(getResponse(fr))
    } catch {
      case ex: Exception => Some(ex.getMessage)
    }
  }

  def getDatabaseList: (List[response.Database], error.Error) = {
    try {
      val url = getUrl("/db")

      val r = getResponse(httpClient.prepareGet(url).execute())
      responseToError(r) match {
        case None => (read[List[response.Database]](r.getResponseBody), None)
        case Some(err) => (Nil, Some(err))
      }
    } catch {
      case ex: Exception => (Nil, Some(ex.getMessage))
    }
  }

  def createDatabaseUser(database: String, username: String, password: String, readFrom: Option[String] = None, writeTo: Option[String] = None): error.Error = {
    try {
      val url = getUrl(s"/db/$database/users")
      val payload = write(Map("name" -> username, "password" -> password, "readFrom" -> readFrom, "writeTo" -> writeTo))

      val fr = httpClient.preparePost(url).addHeader("Content-Type", "application/json").setBody(payload).execute()
      responseToError(getResponse(fr))
    } catch {
      case ex: Exception => Some(ex.getMessage)
    }
  }

  def updateDatabaseUser(database: String, username: String, password: Option[String] = None, isAdmin: Boolean = false, readFrom: Option[String] = None, writeTo: Option[String] = None): error.Error = {
    try {
      val url = getUrl(s"/db/$database/users/$username")
      val payload = write(Map("password" -> password, "admin" -> isAdmin, "readFrom" -> readFrom, "writeTo" -> writeTo))

      val fr = httpClient.preparePost(url).addHeader("Content-Type", "application/json").setBody(payload).execute()
      responseToError(getResponse(fr))
    } catch {
      case ex: Exception => Some(ex.getMessage)
    }
  }

  def authenticateDatabaseUser(database: String, username: String, password: String): error.Error = {
    try {
      val url = getUrlWithUserAndPass(s"/db/$database/authenticate", username, password)
      responseToError(getResponse(httpClient.prepareGet(url).execute()))
    } catch {
      case ex: Exception => Some(ex.getMessage)
    }
  }

  def createClusterAdmin(username: String, password: String): error.Error = {
    try {
      val url = getUrl("/cluster_admins")
      val payload = write(Map("name" -> username, "password" -> password))
      val fr = httpClient.preparePost(url).addHeader("Content-Type", "application/json").setBody(payload).execute()
      responseToError(getResponse(fr))
    } catch {
      case ex: Exception => Some(ex.getMessage)
    }
  }

  def updateClusterAdmin(username: String, password: String): error.Error = {
    try {
      val url = getUrl(s"/cluster_admins/$username")
      val payload = write(Map("password" -> password))

      val fr = httpClient.preparePost(url).addHeader("Content-Type", "application/json").setBody(payload).execute()
      responseToError(getResponse(fr))
    } catch {
      case ex: Exception => Some(ex.getMessage)
    }
  }

  def deleteClusterAdmin(username: String): error.Error = {
    try {
      val url = getUrl(s"/cluster_admins/$username")

      val fr = httpClient.prepareDelete(url).execute()
      responseToError(getResponse(fr))
    } catch {
      case ex: Exception => Some(ex.getMessage)
    }
  }

  def getClusterAdminList: (List[response.ClusterAdmin], error.Error) = {
    try {
      val url = getUrl(s"/cluster_admins")

      val r = getResponse(httpClient.prepareGet(url).execute())
      responseToError(r) match {
        case None => (read[List[response.ClusterAdmin]](r.getResponseBody), None)
        case Some(err) => (Nil, Some(err))
      }
    } catch {
      case ex: Exception => (Nil, Some(ex.getMessage))
    }
  }

  def authenticateClusterAdmin(username: String, password: String): error.Error = {
    try {
      val url = getUrlWithUserAndPass("/cluster_admins/authenticate", username, password)
      responseToError(getResponse(httpClient.prepareGet(url).execute()))
    } catch {
      case ex: Exception => Some(ex.getMessage)
    }
  }

  def query(query: String, timePrecision: Option[String] = None, chunked: Boolean = false): (response.Response, error.Error) = {
    try {
      val q = URLEncoder.encode(query, "UTF-8")
      val url = getUrl(s"/db/$database/series") + s"&q=$q&chunked=$chunked" +
        (if (timePrecision.isDefined) s"&time_precision=${timePrecision.get}" else "")

      val r = getResponse(httpClient.prepareGet(url).execute())
      responseToError(r) match {
        case None => (response.Response(r.getResponseBody), None)
        case Some(err) => (null, Some(err))
      }
    } catch {
      case ex: Exception => (null, Some(ex.getMessage))
    }
  }

  def getContinuousQueries: (List[response.ContinuousQuery], error.Error) = {
    try {
      val url = getUrl(s"/db/$database/continuous_queries")
      val r = getResponse(httpClient.prepareGet(url).execute())
      responseToError(r) match {
        case None => (read[List[response.ContinuousQuery]](r.getResponseBody), None)
        case Some(err) => (Nil, Some(err))
      }
    } catch {
      case ex: Exception => (Nil, Some(ex.getMessage))
    }
  }

  def deleteContinuousQueries(id: Int): error.Error = {
    try {
      val url = getUrl(s"/db/$database/continuous_queries/$id")
      responseToError(getResponse(httpClient.prepareDelete(url).execute()))
    } catch {
      case ex: Exception => Some(ex.getMessage)
    }
  }

  def writeSeries(series: Array[Series]): error.Error = writeSeriesCommon(series, None)

  def writeSeriesWithTimePrecision(series: Array[Series], timePrecision: String): error.Error = {
    writeSeriesCommon(series, Some(Map[String, String]("time_precision" -> timePrecision)))
  }

  private def writeSeriesCommon(series: Array[Series], options: Option[Map[String, String]]): error.Error = {
    try {
      val url = getUrl(s"/db/$database/series") + (if (options.isDefined) options.get.map { o => val (k, v) = o; s"$k=$v" }.mkString("&", "&", "") else "")
      val data = write(series)

      val fr = httpClient.preparePost(url).addHeader("Content-Type", "application/json").setBody(data).execute()
      responseToError(getResponse(fr))
    } catch {
      case ex: Exception => Some(ex.getMessage)
    }
  }
  private def responseToError(r: Response): error.Error = {
    if (r.getStatusCode >= 200 && r.getStatusCode < 300) {
      return None
    }
    return Some(s"Server returned (${r.getStatusText}): ${r.getResponseBody}")
  }
  private def getResponse(fr: Future[Response]): Response = fr.get(timeout, unit)
  private def getUrlWithUserAndPass(path: String, username: String, password: String): String = s"$schema://$host$path?u=$username&p=$password"
  private def getUrl(path: String) = getUrlWithUserAndPass(path, username, password)
}

import scala.util.parsing.json.JSON

package object response {
  case class Database(name: String, replicationFactor: Int)
  case class ClusterAdmin(username: String)
  case class ContinuousQuery(id: Int, query: String)
  case class Response(json: String) {
    
    def toSeries: Array[Series] = {
      val all = JSON.parseFull(json).get.asInstanceOf[List[Any]]
      val series = new Array[Series](all.length)

      var i = 0
      all.foreach { ai =>
        val m = ai.asInstanceOf[Map[String, Any]]
        val name = m.get("name").get.asInstanceOf[String]
        val columns = m.get("columns").get.asInstanceOf[List[String]].toArray
        val points = m.get("points").get.asInstanceOf[List[List[Any]]].map(li => li.toArray).toArray

        series(i) = Series(name, columns, points)
        i += 1
      }
      series
    }

    def toSeriesMap: Array[SeriesMap] = {
      val all = JSON.parseFull(json).get.asInstanceOf[List[Any]]
      val series = new Array[SeriesMap](all.length)

      var i = 0
      all.foreach { ai =>
        val m = ai.asInstanceOf[Map[String, Any]]
        val name = m.get("name").get.asInstanceOf[String]
        val columns = m.get("columns").get.asInstanceOf[List[String]]

        var ii = 0
        val mm = scala.collection.mutable.Map[String, Array[Any]]()
        val cc = new Array[String](columns.size)      
        columns.foreach { cl => cc(ii) = cl; mm(cl) = Array[Any](); ii += 1 }

        m.get("points").get.asInstanceOf[List[List[Any]]].foreach { pt => 
          ii = 0        
          pt.foreach { v => mm += cc(ii) -> (mm(cc(ii)) :+ v); ii += 1; }
        }
        series(i) = SeriesMap(name, mm.toMap)
        i += 1
      }
      series    
    }
  }  
}

package object error {
  type Error = Option[String]
}

case class Series(name: String, columns: Array[String], points: Array[Array[Any]])
case class SeriesMap(name: String, objects: Map[String, Array[Any]])