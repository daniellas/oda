package net.oda

import com.typesafe.scalalogging.Logger
import net.oda.cfd.CFDRest
import net.oda.jira.JiraRest
import net.oda.vertx.VertxServices

object OdaApp {
  val log = Logger("oda");

  def main(args: Array[String]): Unit = {
    log.info("Starting")
    RestApi.init(VertxServices.router)
    CFDRest.init(VertxServices.router)
    JiraRest.init(VertxServices.router)
    VertxServices.httpServer.requestHandler(VertxServices.router.accept).listen
  }
}
