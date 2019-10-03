import net.oda.vertx.VertxServices.Paths
import net.oda.vertx.VertxServices.Paths.path
import org.scalatest.{FreeSpec, Matchers}

class VertxSpec extends FreeSpec with Matchers {
  "Vertx paths" - {
    "should append" in {
      Paths.append("a", "b") should be("ab")
    }
    "should append path" in {
      path("root").andThen(path("sub")).andThen(Paths.variable("x")).apply("/api") should be("/api/root/sub/:x")
    }
  }
}
