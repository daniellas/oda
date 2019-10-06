package net.oda.vertx

import java.util.function.Consumer

import io.vertx.ext.web.RoutingContext

object Handlers {
  def optionBody(b: (RoutingContext) => Option[String]): Consumer[RoutingContext] = {
    ctx => b.apply(ctx).map(ResponseWriters.body).getOrElse(ResponseWriters.notFound).accept(ctx)
  }

  def optionBody[A](mapper: (A) => String, b: (RoutingContext) => Option[A]): Consumer[RoutingContext] = {
    ctx => b.apply(ctx).map(mapper).map(ResponseWriters.body).getOrElse(ResponseWriters.notFound).accept(ctx)
  }

  def body(b: (RoutingContext) => String): Consumer[RoutingContext] = optionBody(b.andThen(Option.apply))

  def body[A](mapper: (A) => String, b: (RoutingContext) => A): Consumer[RoutingContext] = body(b.andThen(mapper))

  val next: Consumer[RoutingContext] = ctx => ctx.next()
}
