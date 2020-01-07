package net.oda.rest.client

import java.util
import java.util.stream.Collectors

import collection.JavaConverters._
import io.vertx.core.{MultiMap, Vertx}
import io.vertx.core.http.{HttpClient, HttpClientResponse, HttpMethod}

import scala.collection.JavaConverters
import scala.collection.JavaConverters._
import scala.concurrent.Promise

/**
 * Some description
 */
object VertxHttpExecutor {
  /**
   *
   * @param vertx
   * @param httpClient
   * @param receiveTimeoutMs
   * @param maxRetryAttempts
   * @param retryDelayer
   * @param errorLog
   * @param debugLog
   * @return HTTP executor instance
   */
  def of(
          vertx: Vertx,
          httpClient: HttpClient,
          receiveTimeoutMs: Long,
          maxRetryAttempts: Int,
          retryDelayer: (Int => Long),
          errorLog: (String, Throwable) => Unit,
          debugLog: String => Unit): HttpExecutor[String] =
    (url, headers, method, body) =>
      retrying(vertx, httpClient, receiveTimeoutMs, maxRetryAttempts, retryDelayer, errorLog, debugLog, Promise(), 1)
        .execute(url, headers, method, body)

  def of(
          vertx: Vertx,
          httpClient: HttpClient,
          errorLog: (String, Throwable) => Unit,
          debugLog: String => Unit): HttpExecutor[String] = of(vertx, httpClient, 10000, 3, _ => 100, errorLog, debugLog)

  private def retrying(
                        vertx: Vertx,
                        httpClient: HttpClient,
                        receiveTimeoutMs: Long,
                        maxRetryAttempts: Int,
                        retryDelayer: (Int => Long),
                        errorLog: (String, Throwable) => Unit,
                        debugLog: String => Unit,
                        prom: Promise[Response[String]],
                        retryAttempt: Int
                      ): HttpExecutor[String] = (url, headers, method, body) => {
    val req = httpClient
      .requestAbs(HttpMethod.valueOf(method), url)
      .handler(processResponse(prom, _))
      .exceptionHandler(
        processException(vertx, httpClient, receiveTimeoutMs, maxRetryAttempts, retryDelayer, errorLog, debugLog, retryAttempt, url, headers, method, body, prom, _))

    headers.foreach(h => req.putHeader(h._1, h._2.asJava))

    debugLog.apply(s"Executing ${method} ${url} ${headers}, attempt ${retryAttempt}")

    if (body.isDefined) {
      req.end(body.get)
    } else {
      req.end
    }

    prom.future
  }

  def mapHeaders(headers: MultiMap): Map[String, Seq[String]] = {
    JavaConverters
      .iterableAsScalaIterable(headers.entries())
      .groupBy(_.getKey)
      .mapValues(_.toSeq.map(_.getValue))
  }

  private def processResponse(prom: Promise[Response[String]], resp: HttpClientResponse): Unit = {
    resp.bodyHandler(b => prom.success(Response(resp.statusCode, resp.statusMessage, mapHeaders(resp.headers), Some(b.toString))))
  }

  private def processException(
                                vertx: Vertx,
                                httpClient: HttpClient,
                                receiveTimeoutMs: Long,
                                maxRetryAttempts: Int,
                                retryDelayer: (Int => Long),
                                errorLog: (String, Throwable) => Unit,
                                debugLog: String => Unit,
                                retryAttempt: Int,
                                url: String,
                                headers: Map[String, Seq[String]],
                                method: String,
                                body: Option[String],
                                prom: Promise[Response[String]],
                                ex: Throwable
                              ): Unit = {
    errorLog.apply(s"Request ${method} ${url} ${headers}, attempt ${retryAttempt} failed", ex)

    if (retryAttempt < maxRetryAttempts + 1) {
      vertx.setTimer(
        retryDelayer.apply(retryAttempt + 1),
        _ => retrying(vertx, httpClient, receiveTimeoutMs, maxRetryAttempts, retryDelayer, errorLog, debugLog, prom, retryAttempt + 1)
          .execute(url, headers, method, body)
      )
    } else {
      prom.failure(ex)
    }
  }
}

