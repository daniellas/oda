package net.oda

import net.oda.rep.cfd.CFDRest
import net.oda.vertx.VertxServices

object OdaApp {
  def main(args: Array[String]): Unit = {
    RestApi.init(VertxServices.router)
    CFDRest.init(VertxServices.router)
    VertxServices.httpServer.requestHandler(VertxServices.router.accept).listen
  }
}
