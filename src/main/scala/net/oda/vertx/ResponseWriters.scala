package net.oda.vertx

import java.util.function.Consumer

import io.netty.handler.codec.http.{HttpHeaderNames, HttpResponseStatus}
import io.vertx.core.http.HttpHeaders
import io.vertx.ext.web.RoutingContext
import org.apache.http.impl.io.HttpRequestWriter

import scala.collection.JavaConverters

object ResponseWriters {
  def header(name: CharSequence, value: CharSequence): Consumer[RoutingContext] = {
    ctx => ctx.response.putHeader(name, value)
  }

  def header(name: CharSequence, values: Seq[CharSequence]): Consumer[RoutingContext] = {
    ctx => ctx.response.putHeader(name, JavaConverters.asJavaIterable(values))
  }


  def contentType(contentType: CharSequence): Consumer[RoutingContext] = header(HttpHeaders.CONTENT_TYPE, contentType)

  val contentTypeJsonUtf8: Consumer[RoutingContext] = contentType("application/json;charset=UTF-8")

  val noCache: Consumer[RoutingContext] = header(HttpHeaders.EXPIRES, "0") andThen
    header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate") andThen
    header(HttpHeaderNames.PRAGMA, "no-cache")

  def body(body: String): Consumer[RoutingContext] = ctx => ctx.response.end(body)

  def body[A](mapper: (A) => String): (A) => Consumer[RoutingContext] = bdy => mapper.andThen(body).apply(bdy)

  def end[A](body: A): Consumer[RoutingContext] = ctx => ctx.response.end

  def end(): Consumer[RoutingContext] = ctx => ctx.response.end

  val notFound: Consumer[RoutingContext] = ctx => ctx.fail(HttpResponseStatus.NOT_FOUND.code)

  val badRequest: Consumer[RoutingContext] = ctx => ctx.fail(HttpResponseStatus.BAD_REQUEST.code)

  val unauthorized: Consumer[RoutingContext] = ctx => ctx.fail(HttpResponseStatus.UNAUTHORIZED.code)

}
