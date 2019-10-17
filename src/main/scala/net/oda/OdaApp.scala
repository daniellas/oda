package net.oda

import net.oda.data.jira.JiraRest
import net.oda.rep.cfd.CFDRest
import net.oda.vertx.VertxServices
import org.slf4j.LoggerFactory

object OdaApp {
  val log = LoggerFactory.getLogger("oda");

  def main(args: Array[String]): Unit = {
    log.info("Starting")
    RestApi.init(VertxServices.router)
    CFDRest.init(VertxServices.router)
    JiraRest.init(VertxServices.router)
    VertxServices.httpServer.requestHandler(VertxServices.router.accept).listen
  }
}
