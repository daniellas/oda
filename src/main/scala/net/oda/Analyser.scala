package net.oda

import net.oda.data.jira.{Issue, JiraClient, JiraTimestampSerializer}
import net.oda.json.JsonSer
import net.oda.rep.CFDReporter
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization

object Analyser {
  implicit val formats = DefaultFormats + JiraTimestampSerializer

  def main(args: Array[String]): Unit = {
    //    downloadJiraData
    generateCfd
  }

  private def downloadJiraData(): Unit = {
    val projectKey = "CRYP"
    val dataLocation = Config.getProp("data.location").getOrElse(() => "./")

    JiraClient.searchIssues
      .andThen(JsonSer.writeAsString(formats, _))
      .andThen(IO.saveTextContent(s"${dataLocation}/${projectKey}-jira-issues.json", _: String))
      .apply(projectKey)
  }

  private def generateCfd(): Unit = {
    val dataLocation = Config.getProp("data.location").getOrElse(() => "./")
    val projectKey = "CRYP"

    IO.loadTextContent
      .andThen(Serialization.read[List[Issue]])
      .andThen(_.map(Mappers.jiraIssueToWorkItem))
      .andThen(CFDReporter.generate)
      .apply(s"${dataLocation}/${projectKey}-jira-issues.json")
  }
}