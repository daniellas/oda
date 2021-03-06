package net.oda.vertx

import io.vertx.ext.web.RoutingContext

import scala.collection.JavaConverters

object RequestReaders {
  val param: (RoutingContext, String) => Option[String] = (ctx: RoutingContext, name: String) => Option(ctx.request.getParam(name))

  val params: (RoutingContext, String) => List[String] = (ctx: RoutingContext, name: String) => JavaConverters.iterableAsScalaIterable(ctx.request.params.getAll(name)).toList

  val body: RoutingContext => Option[String] = (ctx: RoutingContext) => Option(ctx.getBodyAsString)

  val header: (RoutingContext, String) => Option[String] = (ctx: RoutingContext, name: String) => Option(ctx.request.getHeader(name))
}
