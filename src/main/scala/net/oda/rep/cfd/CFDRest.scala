package net.oda.rep.cfd

import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.RoutingContext
import net.oda.RestApi
import net.oda.RestApi.apiRoot
import net.oda.vertx.Handlers
import net.oda.vertx.Paths.path
import net.oda.vertx.VertxServices.router

object CFDRest {

  val root = "cfd"

  def init(): Unit = {
    router
      .route(path(root).apply(apiRoot))
      .method(HttpMethod.GET)
      .blockingHandler(getReport)
  }

  def getReport(ctx: RoutingContext): Unit = {
    Handlers.body(_ => "CFD").accept(ctx)
  }

}
