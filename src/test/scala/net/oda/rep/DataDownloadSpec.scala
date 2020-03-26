package net.oda.rep

import com.typesafe.scalalogging.Logger
import net.oda.gitlab.GitlabClient
import net.oda.jira.{JiraClient, JiraTimestampSerializer}
import net.oda.{Config, FileIO, IT}
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization
import org.scalatest.FreeSpec

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class DataDownloadSpec extends FreeSpec {
  val log = Logger(classOf[DataDownloadSpec])
  implicit val jiraFormats = DefaultFormats + JiraTimestampSerializer

  "Download Gitlab projects" taggedAs (IT) in {
    val res = GitlabClient.getProjects()
      .map(Serialization.write(_))

    res.onComplete(_.foreach(FileIO.saveTextContent(s"${Config.dataLocation}/gitlab-projects.json", _: String)))

    Await.result(res, 10 minutes)
  }

  "Download JIRA issues" taggedAs (IT) in {
    Config.props.jira.projects.keys.foreach(downloadJiraData)
  }

  def downloadJiraData(projectKey: String) = {
    log.info("Downloading {} JIRA data", projectKey)

    val res = JiraClient.searchIssues(projectKey)
      .map(Serialization.write(_)(jiraFormats))

    res.onComplete(_.foreach(FileIO.saveTextContent(s"${Config.dataLocation}/jira-issues-${projectKey}.json", _: String)))

    Await.result(res, 10 minutes)
    log.info("{} JIRA data downloaded", projectKey)
  }
}
