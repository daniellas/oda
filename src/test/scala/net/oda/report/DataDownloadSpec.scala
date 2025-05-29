package net.oda.report

import com.typesafe.scalalogging.Logger
import net.oda.FileIO.saveTextContent
import net.oda.gitlab.{GitlabClient, GitlabData}
import net.oda.jira.{JiraClient, JiraDateSerializer, JiraIssues, JiraTimestampSerializer}
import net.oda.{Config, IT, Spark}
import org.apache.spark.sql.SaveMode
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization
import org.scalatest.FreeSpec

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{ZoneId, ZonedDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class DataDownloadSpec extends FreeSpec {
  val log = Logger(classOf[DataDownloadSpec])
  implicit val jsonFormats = DefaultFormats + JiraTimestampSerializer + JiraDateSerializer
  val defaultStart = ZonedDateTime.now().minusYears(10)
  val interval = 12

  "Download data" taggedAs (IT) in {
    val start = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault())
    val end = ZonedDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0)

    //    downloadGitlabProjects()
    //    downloadGitlabMergeRequests(start, end)
    //    downloadJiraIssuesData()
    //    downloadGitlabCommits(end.withDayOfMonth(1), end.plusMonths(1).withDayOfMonth(1))
    //    downloadAllGitlabCommits(start, end)

    uploadDataToHdfs()
  }

  def downloadAllGitlabCommits(start: ZonedDateTime, end: ZonedDateTime): Unit = {
    val months = ChronoUnit.MONTHS.between(
      start.withDayOfMonth(1),
      end.withDayOfMonth(1))
      .toInt

    (0 to months)
      .map(m => start.withDayOfMonth(1).plusMonths(m))
      .foreach(m => downloadGitlabCommits(m, m.plusMonths(1)))
  }

  def downloadGitlabProjects(): Unit = {
    log.info("Downloading Gitlab projects")

    val projects = GitlabClient.getProjects()
      .map(Serialization.write(_))

    projects.onComplete(_.foreach(saveTextContent(s"${Config.dataLocation}/gitlab-projects.json", _: String)))
    Await.result(projects, 10 minutes)
  }

  def downloadGitlabMergeRequests(after: ZonedDateTime, before: ZonedDateTime) = {
    log.info("Downloading Gitlab merge requests")

    val mergeRequests = GitlabClient.getMergeRequests(after, before)
      .map(Serialization.write(_))

    mergeRequests.onComplete(_.foreach(saveTextContent(s"${Config.dataLocation}/gitlab-merge-requests.json", _: String)))

    Await.result(mergeRequests, 20 minutes)
  }

  def downloadGitlabCommits(since: ZonedDateTime, until: ZonedDateTime) = {
    log.info("Downloading commits between {} - {}", since, until)

    val commits = Future.sequence(
      GitlabData
        .loadProjects()
        .map(p =>
          GitlabClient.getCommits(p.id, "master", true, since, until)
            .map(cs => cs.filterNot(_.committer_email.startsWith("jenkins"))
              .map(_.mapCommitterEmail(Config.props.emailMapping))
              .map(c => (p, c)))))
      .map(_.flatten)
      .map(pcs => pcs.map(pc => CommitItem(
        pc._2.id,
        pc._2.title,
        pc._1.id,
        pc._2.created_at,
        pc._2.committer_email,
        pc._2.stats.additions,
        pc._2.stats.deletions,
        pc._2.stats.total
      )))
      .map(Serialization.write(_))

    val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    commits.onComplete(_.foreach(
      saveTextContent(
        s"${Config.dataLocation}/commits/gitlab-commits-" +
          s"${since.format(dateTimeFormatter)}-${until.format(dateTimeFormatter)}.json",
        _: String)))

    Await.result(commits, 20 minutes)

  }

  case class CommitItem(
                         id: String,
                         title: String,
                         projectId: Int,
                         createdAt: ZonedDateTime,
                         committer: String,
                         additions: Int,
                         deletions: Int,
                         total: Int)

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
      .map(saveTextContent(s"${Config.dataLocation}/jira-issues-${projectKey}.json", _))

    Await.result(path, 10 minutes)
    log.info("{} JIRA issues data downloaded to {}", projectKey, path)
  }

  def downloadJiraVersionsData(projectKey: String) = {
    Await.result(
      JiraClient.getProjectVersions(projectKey)
        .map(Serialization.write(_)(jsonFormats))
        .map(saveTextContent(s"${Config.dataLocation}/jira-project-versions-${projectKey}.json", _)),
      10 minutes)
  }

  def uploadDataToHdfs() = {
    Spark.session.read.json(s"${Config.dataLocation}/gitlab-projects.json")
      .write.mode(SaveMode.Overwrite).json("hdfs://hdfs.platform:9000/user/daniel.las/teams-metrics/gitlab-projects.json")
    Spark.session.read.json(s"${Config.dataLocation}/gitlab-merge-requests.json")
      .write.mode(SaveMode.Overwrite).json("hdfs://hdfs.platform:9000/user/daniel.las/teams-metrics/gitlab-merge-requests.json")
    Spark.session.read.json(s"${Config.dataLocation}/commits/*.json")
      .write.mode(SaveMode.Overwrite).json("hdfs://hdfs.platform:9000/user/daniel.las/teams-metrics/gitlab-commits.json")
    Config.props.jira.projects.keys.foreach(projectKey => {
      Spark.session.read.json(s"${Config.dataLocation}/jira-issues-${projectKey}.json")
        .write.mode(SaveMode.Overwrite).json(s"hdfs://hdfs.platform:9000/user/daniel.las/teams-metrics/jira-issues-${projectKey}.json")
    })
    Spark.session.read.csv(s"${Config.dataLocation}/timesheets/*.csv")
      .write.mode(SaveMode.Overwrite).csv("hdfs://hdfs.platform:9000/user/daniel.las/teams-metrics/timesheets.csv")
  }
}
