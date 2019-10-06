package net.oda

import net.oda.data.jira.{Issue, JiraClient, JiraTimestampSerializer}
import net.oda.json.JsonSer
import net.oda.cfd.{CFDReporter, CFDRest}
import net.oda.vertx.VertxServices
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization
import org.slf4j.LoggerFactory

object Analyser {
  val log = LoggerFactory.getLogger("oda")

  implicit val formats = DefaultFormats + JiraTimestampSerializer

  def main(args: Array[String]): Unit = {
    //    downloadJiraData
    //        generateCfd
    RestApi.init(VertxServices.router)
    CFDRest.init
    VertxServices.httpServer.requestHandler(VertxServices.router.accept).listen
  }

  private def downloadJiraData(): Unit = {
    val projectKey = "CRYP"
    val dataLocation = Config.getProp("data.location").getOrElse(() => "./")

    log.info("Downloading JIRA data")
    JiraClient.searchIssues
      .andThen(Serialization.write(_)(formats))
      .andThen(IO.saveTextContent(s"${dataLocation}/${projectKey}-jira-issues.json", _: String))
      .apply(projectKey)
    log.info("JIRA data downloaded")
  }

  private def generateCfd(): Unit = {
    val dataLocation = Config.getProp("data.location").getOrElse(() => "./")
    val projectKey = "CRYP"

    IO.loadTextContent
      .andThen(Serialization.read[List[Issue]])
      .andThen(_.map(Mappers.jiraIssueToWorkItem))
      .andThen(CFDReporter.generate(projectKey, _))
      .apply(s"${dataLocation}/${projectKey}-jira-issues.json")
  }
}