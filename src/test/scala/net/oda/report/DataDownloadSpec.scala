package net.oda.report

import java.time.{ZoneId, ZonedDateTime}
import com.typesafe.scalalogging.Logger
import net.oda.gitlab.{GitlabClient, GitlabInflux}
import net.oda.jira.{JiraClient, JiraDateSerializer, JiraIssues, JiraTimestampSerializer}
import net.oda.{Config, FileIO, IT}
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization
import org.scalatest.FreeSpec

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class DataDownloadSpec extends FreeSpec {
  val log = Logger(classOf[DataDownloadSpec])
  implicit val jsonFormats = DefaultFormats + JiraTimestampSerializer + JiraDateSerializer
  val defaultStart = ZonedDateTime.now().minusYears(10)
  val interval = 12

  "Download data" taggedAs (IT) in {
    val start = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault())
    val end = ZonedDateTime.now()

    //    downloadGitlabProjects()
    //    downloadGitlabMergeRequests(start, end)
    //    downloadGitlabCommits()
    downloadJiraIssuesData()
    //downloadJiraVersionsData()
  }

  def downloadGitlabProjects(): Unit = {
    val projects = GitlabClient.getProjects()
      .map(Serialization.write(_))

    projects.onComplete(_.foreach(FileIO.saveTextContent(s"${Config.dataLocation}/gitlab-projects.json", _: String)))
    Await.result(projects, 10 minutes)
  }

  def downloadGitlabMergeRequests(after: ZonedDateTime, before: ZonedDateTime) = {
    val mergeRequests = GitlabClient.getMergeRequests(after, before)
      .map(Serialization.write(_))

    mergeRequests.onComplete(_.foreach(FileIO.saveTextContent(s"${Config.dataLocation}/gitlab-merge-requests.json", _: String)))

    Await.result(mergeRequests, 20 minutes)
  }

  def downloadGitlabCommits() = {
    val promise = GitlabInflux.findLastCommitTime()
      .map(_.getOrElse(defaultStart))
      .flatMap(s => ReportsGenerator.commits(s, s.plusMonths(interval)))

    Await.result(promise, 20 minutes)
  }


  def downloadJiraIssuesData(): Unit = {
    Config.props.jira.projects.keys.foreach(downloadJiraIssuesData)
  }

  def downloadJiraVersionsData(): Unit = {
    Config.props.jira.projects.keys.foreach(downloadJiraVersionsData)
  }

  def downloadJiraIssuesData(projectKey: String) = {
    log.info("Downloading {} JIRA issues data", projectKey)

    val path = JiraClient.searchIssues(projectKey)
      .map(_.map(JiraIssues.trimHistoryItems))
      .map(Serialization.write(_)(jsonFormats))
      .map(FileIO.saveTextContent(s"${Config.dataLocation}/jira-issues-${projectKey}.json", _))

    Await.result(path, 10 minutes)
    log.info("{} JIRA issues data downloaded to {}", projectKey, path)
  }

  def downloadJiraVersionsData(projectKey: String) = {
    Await.result(
      JiraClient.getProjectVersions(projectKey)
        .map(Serialization.write(_)(jsonFormats))
        .map(FileIO.saveTextContent(s"${Config.dataLocation}/jira-project-versions-${projectKey}.json", _)),
      10 minutes)
  }

}
