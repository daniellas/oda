package net.oda.vertx

import net.oda.vertx.Paths.{path, variable}
import net.oda.vertx.ResponseWriters.{contentTypeJsonUtf8, header}
import org.scalatest.{FreeSpec, Matchers}

class VertxSpec extends FreeSpec with Matchers {
  "Vertx paths" - {
    "should append path" in {
      path("root").andThen(path("sub")).andThen(variable("x")).apply("/api") should be("/api/root/sub/:x")
    }
  }
  "Vertx response writers" - {
    "should be combinable" in {
      header("a", "b") andThen
        header("x", "y") andThen
        contentTypeJsonUtf8 andThen
        header("x", "y") accept null
    }
  }
}
