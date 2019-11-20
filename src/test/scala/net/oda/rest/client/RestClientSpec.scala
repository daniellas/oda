package net.oda.rest.client


import com.typesafe.scalalogging.Logger
import io.vertx.core.Vertx
import io.vertx.core.http.{HttpClientOptions, HttpMethod, HttpServerOptions}
import org.scalatest.{AsyncFlatSpec, BeforeAndAfter, Matchers}

class RestClientSpec extends AsyncFlatSpec with BeforeAndAfter with Matchers {
  val log = Logger(classOf[RestClientSpec])
  val vertx = Vertx.vertx
  val port = 8070
  val httpServer = vertx.createHttpServer(new HttpServerOptions().setPort(port))
    .requestHandler(r =>
      r.method match {
        case HttpMethod.GET => r.response.putHeader("Content-Length", "3").write("GET").end
        case _ => r.response.end
      }
    )
    .listen
  val httpClient = vertx.createHttpClient(new HttpClientOptions().setDefaultPort(port))
  val restClient = RestClient
    .using(
      VertxHttpExecutor.of(vertx, httpClient, 100, 3, _ => 1000, (m, e) => log.error(m, e), m => log.debug(m)),
      s => if (s == "local") "http://localhost:" + port else "http://localhost:" + (port + 1))

  it should "GET" in {
    restClient
      .service("local")
      .noDefaultHeaders
      .resource("/get/%s", "%")
      .get
      .execute
      .map(r => r.body should be(Some("GET")))
  }

  it should "Retry GET" in {
    restClient
      .service("fail")
      .noDefaultHeaders
      .resource("/get")
      .get
      .execute
      .map(r => r should be(None))
  }

}
