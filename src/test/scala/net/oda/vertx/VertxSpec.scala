package net.oda.vertx

import net.oda.vertx.Paths.{path, variable}
import org.scalatest.{FreeSpec, Matchers}

class VertxSpec extends FreeSpec with Matchers {
  "Vertx paths" - {
    "should append path" in {
      path("root").andThen(path("sub")).andThen(variable("x")).apply("/api") should be("/api/root/sub/:x")
    }
  }
}
