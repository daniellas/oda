package net.oda.vertx

import io.vertx.core.Vertx
import io.vertx.core.http.{HttpClient, HttpServer, HttpServerOptions}
import io.vertx.ext.web.Router
import net.oda.Config.getProp

object Paths {
  private val append = (l: String, r: String) => l + r
  private val separator = (v: String) => v + "/"
  val path: String => String => String = (v: String) => separator.andThen(append(_, v))
  val variable: String => String => String = (v: String) => separator.andThen(append(_, ":")).andThen(append(_, v))
}

object VertxServices {
  val vertx: Vertx = Vertx.vertx

  val httpClient: HttpClient = vertx.createHttpClient

  val httpServer: HttpServer = vertx.createHttpServer(
    new HttpServerOptions()
      .setPort(getProp("http.port").map(_.toInt).getOrElse(8080)))

  val router: Router = Router.router(vertx)
}
