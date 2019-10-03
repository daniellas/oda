package net.oda.vertx

import io.vertx.core.Vertx
import io.vertx.core.http.{HttpClient, HttpServer, HttpServerOptions}
import io.vertx.ext.web.Router
import net.oda.Config

object VertxServices {
  val vertx: Vertx = Vertx.vertx
  val httpClient: HttpClient = vertx.createHttpClient
  val httpServer: HttpServer = vertx.createHttpServer(
    new HttpServerOptions()
      .setPort(Config.getProp("http.port").map(_.toInt).getOrElse(8080)))
  val router: Router = Router.router(vertx)

  object Paths {
    val append = (l: String, r: String) => l + r
    val separator = (v: String) => v + "/"
    val path = (v: String) => separator.andThen(append(_, v))
    val variable = (v: String) => separator.andThen(append(_, ":")).andThen(append(_, v))
  }

}
