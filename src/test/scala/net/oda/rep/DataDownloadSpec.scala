package net.oda.rep

import java.time.ZonedDateTime

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
  implicit val jsonFormats = DefaultFormats + JiraTimestampSerializer
  val months = 1

  "Download data" taggedAs (IT) in {
    downloadGitlabData()
    downloadJiraData()
  }

  def downloadGitlabData() = {
    val projects = GitlabClient.getProjects()
      .map(Serialization.write(_))

    projects.onComplete(_.foreach(FileIO.saveTextContent(s"${Config.dataLocation}/gitlab-projects.json", _: String)))
    Await.result(projects, 10 minutes)
    Await.result(ReportsGenerator.commits(ZonedDateTime.now().minusMonths(months)), 20 minutes)
    Await.result(ReportsGenerator.mergeRequests(ZonedDateTime.now().minusMonths(6 * months)), 20 minutes)
  }

  def downloadJiraData(): Unit = {
    Config.props.jira.projects.keys.foreach(downloadJiraProjectData)
  }

  def downloadJiraProjectData(projectKey: String) = {
    log.info("Downloading {} JIRA data", projectKey)

    val res = JiraClient.searchIssues(projectKey)
      .map(Serialization.write(_)(jsonFormats))

    res.onComplete(_.foreach(FileIO.saveTextContent(s"${Config.dataLocation}/jira-issues-${projectKey}.json", _: String)))

    Await.result(res, 10 minutes)
    log.info("{} JIRA data downloaded", projectKey)
  }
}
