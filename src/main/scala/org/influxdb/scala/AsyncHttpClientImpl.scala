package org.influxdb.scala

import scala.concurrent.Future
import java.util.concurrent.ExecutorService
import com.ning.http.client.AsyncHttpClient
import scala.concurrent.Promise
import org.influxdb.scala.HTTPServiceComponent.HTTPService

/**
 * Implementation of HTTPService using the (java) AsyncHttpClient library
 */
class AsyncHttpClientImpl(implicit pool:ExecutorService) extends HTTPService {

  def GET(url: String): Future[String] = {
    val client = new AsyncHttpClient()
    val f = client.prepareGet(url).execute()
    val p = Promise[String]()
    f.addListener(new Runnable() {
      def run = {
        val response = f.get
        if (response.getStatusCode() < 400) {
          p.success(response.getResponseBody)
        } else {
          p.failure(new RuntimeException(s"Error response: ${response.getStatusCode()}: ${response.getResponseBody()}"))
        }
        client.close
      }
    }, pool)
    p.future
  }

  def POST(url: String, body: String, contentType: String): Future[Unit] = {
    val p = Promise[Unit]()
    val client = new AsyncHttpClient()
    val f = client.preparePost(url).setBody(body).addHeader("Content-Type", "application/json").execute
    f.addListener(new Runnable() {
      def run {
        val response = f.get
        if (response.getStatusCode() < 400) {
          p.success()
        } else {
          p.failure(new RuntimeException(s"Error response: ${response.getStatusCode}: ${response.getResponseBody}"))
        }
        client.close
      }
    }, pool)
    p.future
  }
}