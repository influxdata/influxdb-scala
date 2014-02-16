package org.influxdb.scala

import scala.concurrent.Future
import java.util.concurrent.{Executors, ExecutorService}
import com.ning.http.client.{Response, ListenableFuture, AsyncHttpClient}
import scala.concurrent.Promise
import scala.concurrent.duration.Duration

/**
 * Implementation of HTTPService using the (java) AsyncHttpClient library
 */
trait AsyncHttpClientComponent extends  HTTPServiceComponent {

  implicit val pool = Executors.newSingleThreadExecutor()
  val httpService = new AsyncHttpClientImpl()

  // this is defined here since it is purely for the executorService created here
  def shutdown(timeout: Duration) {
    pool.awaitTermination(timeout.length, timeout.unit)
    httpService.close
    pool.shutdown()
  }

  class AsyncHttpClientImpl(implicit pool:ExecutorService) extends HTTPService {

    val client = new AsyncHttpClient()

    def close = client.close()

    def getOrDelete[T](builder: AsyncHttpClient#BoundRequestBuilder)(transformResponse: String => T): Future[T] = {
      val p = Promise[T]()
      setupListener[T](builder.execute(), p)(transformResponse)
      p.future
    }

    private def setupListener[T](f:ListenableFuture[Response],p:Promise[T])(transformResponse: String => T) {
      f.addListener(new Runnable() {
        def run = {
          val response = f.get
          if (response.getStatusCode() < 400) {
            val body = response.getResponseBody
            LOG.debug(s"${response.getUri} =>\n$body")
            p.success(transformResponse(body))
          } else {
            p.failure(new RuntimeException(s"Error response: ${response.getStatusCode()}: ${response.getResponseBody()}"))
          }
        }
      }, pool)
    }

    private def postOrPut(builder: AsyncHttpClient#BoundRequestBuilder, body: String, contentType: String):Future[Unit] = {
      val p = Promise[Unit]()
      val f = builder.setBody(body).addHeader("Content-Type", "application/json").execute
      setupListener[Unit](f,p) {result => }
      p.future
    }

    def GET(url: String): Future[String] = getOrDelete[String](client.prepareGet(url)) { result => result}
    def POST(url: String, body: String, contentType: String): Future[Unit] = postOrPut(client.preparePost(url),body,contentType)
    def PUT(url: String, body: String, contentType: String):  Future[Unit] = postOrPut(client.preparePut(url),body,contentType)
    def DELETE(url:String):Future[Unit]  = getOrDelete[Unit](client.prepareDelete(url)) { result => }

  }
}