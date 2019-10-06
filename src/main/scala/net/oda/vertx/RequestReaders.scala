package net.oda.vertx

import java.util

import io.vertx.ext.web.RoutingContext

object RequestReaders {
  val param: (RoutingContext, String) => Option[String] = (ctx: RoutingContext, name: String) => Option(ctx.request.getParam(name))

  val params: (RoutingContext, String) => List[util.List[String]] = (ctx: RoutingContext, name: String) => List(ctx.request.params.getAll(name))

  val body: RoutingContext => Option[String] = (ctx: RoutingContext) => Option(ctx.getBodyAsString)

  val header: (RoutingContext, String) => Option[String] = (ctx: RoutingContext, name: String) => Option(ctx.request.getHeader(name))
}
