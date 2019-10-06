package net.oda

import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.CorsHandler
import net.oda.vertx.Handlers.next
import net.oda.vertx.ResponseWriters.{contentTypeJsonUtf8, noCache}

object RestApi {
  val apiRoot = "/oda/api"

  def init(router: Router): Unit = {
    router
      .route
      .order(-2)
      .handler(CorsHandler.create("*")
        .allowedHeader("*")
        .exposedHeader("*")
        .allowedMethod(HttpMethod.GET)
        .allowedMethod(HttpMethod.PUT)
        .allowedMethod(HttpMethod.POST)
        .allowedMethod(HttpMethod.PATCH)
        .allowedMethod(HttpMethod.DELETE)
        .allowedMethod(HttpMethod.OPTIONS))

    router.route(apiRoot + "/*")
      .order(-1)
      .handler(ctx => contentTypeJsonUtf8
        .andThen(noCache)
        .andThen(next)
        .accept(ctx))
  }
}
