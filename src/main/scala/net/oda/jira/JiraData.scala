package net.oda.jira

import net.oda.{Config, FileIO}
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization

object JiraData {
  implicit val formats = DefaultFormats + JiraTimestampSerializer

  val location = (projectKey: String) => s"${Config.dataLocation}/jira-issues-${projectKey}.json"
  val load = FileIO.loadTextContent.andThen(Serialization.read[List[Issue]])
  val loadAsWorkItems = load.andThen(_.map(Mappers.jiraIssueToWorkItem(_, _ => Some(0))))
}
