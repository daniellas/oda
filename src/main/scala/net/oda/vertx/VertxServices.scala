package net.oda.vertx

import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient

object VertxServices {
  val vertx: Vertx = Vertx.vertx

  var httpClient: HttpClient = vertx.createHttpClient
}
