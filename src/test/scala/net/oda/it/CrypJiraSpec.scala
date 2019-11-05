package net.oda.it

import net.oda.data.jira.{JiraClient, JiraTimestampSerializer}
import net.oda.{Config, FileIO, IT}
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization
import org.scalatest.FreeSpec
import org.slf4j.LoggerFactory

class CrypJiraSpec extends FreeSpec {
  val log = LoggerFactory.getLogger("oda")
  implicit val formats = DefaultFormats + JiraTimestampSerializer
  val projectKey = "CRYP"

  s"Download ${projectKey} issues" taggedAs (IT) in {
    log.info("Downloading {} JIRA data", projectKey)
    JiraClient.searchIssues
      .andThen(Serialization.write(_)(formats))
      .andThen(FileIO.saveTextContent(s"${Config.dataLocation}/jira-issues-${projectKey}.json", _: String))
      .apply(projectKey)
    log.info("{} JIRA data downloaded", projectKey)
  }

}
