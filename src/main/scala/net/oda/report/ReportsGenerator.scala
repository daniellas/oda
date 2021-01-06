package net.oda.report

import java.time.temporal.ChronoUnit
import java.time.{LocalDate, ZonedDateTime}
import java.util.concurrent.Executors

import com.paulgoldbaum.influxdbclient.Parameter.Precision
import com.paulgoldbaum.influxdbclient.Point
import net.oda.Config
import net.oda.cfd.CfdInflux.toCfdCountPoints
import net.oda.cfd.CfdReporter.generate
import net.oda.cfd.{CfdInflux, CfdReporter}
import net.oda.gitlab._
import net.oda.influx.Influx
import net.oda.jira.JiraData.location
import net.oda.jira.{JiraData, JiraInflux, JiraReporter}

import scala.concurrent.{ExecutionContext, Future}


object ReportsGenerator {

  implicit val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(1))

  def workItemsChangelog(projectKey: String, interval: ChronoUnit) = {
    JiraData
      .loadAsWorkItems(Config.props.jira.projects(projectKey).estimateMapping.get)
      .andThen(JiraReporter.workItemsChangeLog(_, interval))
      .andThen(JiraInflux.workItemsChangelog(_, projectKey, interval.name))
      .andThen(writeChunks)
      .apply(JiraData.location(projectKey))
  }

  def jiraCountByTypePriority(projectKey: String, interval: ChronoUnit, stateMapping: Map[String, String]) = {
    JiraData
      .loadAsWorkItems(Config.props.jira.projects(projectKey).estimateMapping.get)
      .andThen(JiraReporter.countByTypePriority(_, stateMapping, interval))
      .andThen(JiraInflux.countByTypePriorityPoints(_, projectKey, interval.name))
      .andThen(writeChunks)
      .apply(JiraData.location(projectKey))
  }

  def jiraCountDistinctAuthors(projectKey: String, interval: ChronoUnit, stateFilter: String => Boolean, qualifier: String) = {
    JiraData
      .loadAsWorkItems(Config.props.jira.projects(projectKey).estimateMapping.get)
      .andThen(JiraReporter.countDistinctAuthor(_, stateFilter, interval))
      .andThen(JiraInflux.countDistinctAuthorsPoints(_, projectKey, interval.name, qualifier))
      .andThen(writeChunks)
      .apply(JiraData.location(projectKey))
  }

  def jiraCountCfd(
                    projectKey: String,
                    entryState: String,
                    finalState: String,
                    stateMapping: Map[String, String],
                    referenceFlow: Map[String, Int],
                    types: String => Boolean,
                    priorities: String => Boolean,
                    interval: ChronoUnit,
                    qualifier: String
                  ) = {
    JiraData
      .loadAsWorkItems(Config.props.jira.projects(projectKey).estimateMapping.get)
      .andThen(
        generate(
          projectKey,
          LocalDate.MIN,
          types,
          priorities,
          referenceFlow,
          entryState,
          finalState,
          stateMapping,
          interval,
          CfdReporter.countAggregate,
          _))
      .andThen(toCfdCountPoints("cfd_count", _, projectKey, qualifier, entryState, finalState, interval.name))
      .andThen(writeChunks)
      .apply(JiraData.location(projectKey))
  }

  def jiraEstimateCfd(
                       projectKey: String,
                       entryState: String,
                       finalState: String,
                       stateMapping: Map[String, String],
                       referenceFlow: Map[String, Int],
                       types: String => Boolean,
                       priorities: String => Boolean,
                       interval: ChronoUnit,
                       qualifier: String
                     ) = {
    JiraData
      .loadAsWorkItems(Config.props.jira.projects(projectKey).estimateMapping.get)
      .andThen(
        generate(projectKey, LocalDate.MIN, types, priorities, referenceFlow, entryState, finalState, stateMapping, interval, CfdReporter.sumEstimateAggregate, _))
      .andThen(CfdInflux.toCfdEstimatePoints("cfd_estimate", _, projectKey, qualifier, entryState, finalState, interval.name))
      .andThen(writeChunks)
      .apply(JiraData.location(projectKey))
  }

  def teamProductivityFactor(
                              projectKey: String,
                              stateFilter: String => Boolean,
                              interval: ChronoUnit,
                              learningTime: Double
                            ) = {
    JiraData
      .loadAsWorkItems(Config.props.jira.projects(projectKey).estimateMapping.get)
      .andThen(JiraReporter.teamProductivityFactor(_, stateFilter, interval, learningTime))
      .andThen(JiraInflux.teamProductivityFactor(_, projectKey, interval.name()))
      .andThen(writeChunks)
      .apply(JiraData.location(projectKey))
  }

  def workItemsDuration(
                         projectKey: String,
                         entryState: String,
                         finalState: String,
                         stateMapping: Map[String, String],
                         referenceFlow: Map[String, Int],
                         types: String => Boolean,
                         interval: ChronoUnit,
                         qualifier: String
                       ) = {
    JiraData
      .loadAsWorkItems(Config.props.jira.projects(projectKey).estimateMapping.get)
      .andThen(
        CfdReporter
          .calculateWorkItemsDuration(
            projectKey,
            LocalDate.MIN,
            types,
            _ => true,
            referenceFlow,
            entryState,
            finalState,
            stateMapping,
            interval,
            _))
      .andThen(CfdInflux.toCfdDurationsPoints(_, projectKey, qualifier, interval.name()))
      .andThen(writeChunks)
      .apply(location(projectKey))
  }

  def workItemsCountByState(
                             projectKey: String,
                             interval: ChronoUnit
                           ) = {
    JiraData
      .loadAsWorkItems(Config.props.jira.projects(projectKey).estimateMapping.get)
      .andThen(JiraReporter.countByState(_, interval))
      .andThen(JiraInflux.workItemsCountByStatePoints(_, projectKey, interval.name()))
      .andThen(writeChunks)
      .apply(JiraData.location(projectKey))
  }

  def workItemsCountByStateMovingAverage(
                                          projectKey: String,
                                          duration: Long,
                                          interval: ChronoUnit
                                        ) = {
    JiraData
      .loadAsWorkItems(Config.props.jira.projects(projectKey).estimateMapping.get)
      .andThen(JiraReporter.countByStateMovingAverage(_, duration, interval))
      .andThen(JiraInflux.workItemsCountByStateMovingAveragePoints(_, projectKey, interval.name()))
      .andThen(writeChunks)
      .apply(JiraData.location(projectKey))
  }

  def commits(since: ZonedDateTime, until: ZonedDateTime) = Future.sequence(
    GitlabData
      .loadProjects()
      .map(p =>
        GitlabClient.getCommits(p.id, "develop", true, since, until)
          .map(cs => cs.filterNot(_.committer_email.startsWith("jenkins"))
            .map(_.mapCommitterEmail(Config.props.emailMapping))
            .map(c => (p, c)))))
    .map(_.flatten)
    .map(GitlabInflux.toCommitsPoints)
    .map(writeChunks)

  def namespaceActivityRank(interval: ChronoUnit, max: Int) = GitlabInflux
    .loadCommits()
    .map(GitlabReporter.namespaceActivityRank(_, interval))
    .map(GitlabInflux.toNamespaceActivityRankPoints(_, interval))
    .map(writeChunks)


  def reposActivityRank(interval: ChronoUnit, max: Int) = GitlabInflux
    .loadCommits()
    .map(GitlabReporter.reposActivityRank(_, interval))
    .map(GitlabInflux.toReposActivityRankPoints(_, interval))
    .map(writeChunks)

  def committersActivityRank(interval: ChronoUnit, max: Int) = GitlabInflux
    .loadCommits()
    .map(GitlabReporter.committersActivityRank(_, interval))
    .map(GitlabInflux.toCommittersActivityPoints(_, interval))
    .map(writeChunks)

  def commitsStats(interval: ChronoUnit) = GitlabInflux
    .loadCommits()
    .map(CommitsReporter.commitsStats(_, interval))
    .map(GitlabInflux.toCommitsStatsPoints(_, interval))
    .map(writeChunks)

  def commitsSummary(interval: ChronoUnit) = GitlabInflux
    .loadCommits()
    .map(CommitsReporter.commitsSummary(_, interval))
    .map(GitlabInflux.toCommitsSummaryPoints(_, interval))
    .map(writeChunks)

  def commitsByNamespace(interval: ChronoUnit) = GitlabInflux
    .loadCommits()
    .map(CommitsReporter.commitsByNamespace(_, interval))
    .map(GitlabInflux.toCommitsByNamespacePoints(_, interval))
    .map(writeChunks)

  def activeCommitters(interval: ChronoUnit) = GitlabInflux
    .loadCommits()
    .map(CommitsReporter.activeCommitters(_, interval))
    .map(GitlabInflux.toActiveCommittersPoints(_, interval))
    .map(writeChunks)

  def mergeRequests(after: ZonedDateTime, before: ZonedDateTime) = GitlabClient
    .getMergeRequests(after, before)
    .map(GitlabInflux.toMergeRequestsPoints)
    .map(writeChunks)

  def mergeRequestsByState(targetBranch: String, interval: ChronoUnit) = GitlabInflux
    .loadMergeRequests()
    .map(MergeRequestsReporter.mergeRequestsByState(_, GitlabData.loadProjects(), targetBranch, interval))
    .map(GitlabInflux.toMergeRequestsByStatePoints(_, interval))
    .map(writeChunks)

  def mergeRequestsByAuthor(targetBranch: String, interval: ChronoUnit) = GitlabInflux
    .loadMergeRequests()
    .map(MergeRequestsReporter.mergeRequestsByAuthor(_, targetBranch, interval))
    .map(GitlabInflux.toMergeRequestsByAuthorPoints(_, interval))
    .map(writeChunks)

  def mergeRequestsMovingAverage(targetBranch: String, interval: ChronoUnit) = GitlabInflux
    .loadMergeRequests()
    .map(MergeRequestsReporter.mergeRequestsMovingAverage(_, GitlabData.loadProjects(), targetBranch, interval))
    .map(GitlabInflux.toMergeRequestsMovingAveragePoints(_, interval))
    .map(writeChunks)

  def mergeRequestsComments(targetBranch: String, interval: ChronoUnit) = GitlabInflux
    .loadMergeRequests()
    .map(MergeRequestsReporter.mergeRequestsComments(_, targetBranch, interval))
    .map(GitlabInflux.toMergeRequestsCommentsPoints(_, interval))
    .map(writeChunks)

  def mergeRequestsDuration(targetBranch: String, interval: ChronoUnit) = GitlabInflux
    .loadMergeRequests()
    .map(MergeRequestsReporter.mergeRequestsDuration(_, GitlabData.loadProjects(), targetBranch, interval))
    .map(GitlabInflux.toMergeRequestsDurationPoints(_, interval))
    .map(writeChunks)

  def mergeRequestsAuthorsRatio(targetBranch: String, interval: ChronoUnit) = GitlabInflux
    .loadMergeRequests()
    .map(MergeRequestsReporter.mergeRequestsAuthorsRatio(_, targetBranch, interval))
    .map(GitlabInflux.toMergeRequestsAuthorsRatioPoints(_, interval))
    .map(writeChunks)

  def commitsStatsByProjectRole(interval: ChronoUnit) = GitlabInflux
    .loadCommits()
    .map(CommitsReporter.commitsStatsByProjectRole(GitlabData.loadProjects(), _, interval))
    .map(GitlabInflux.toCommitsStatsByProjectRolePoints(_, interval))
    .map(writeChunks)

  def commitsStatsByProjectCategory(interval: ChronoUnit) = GitlabInflux
    .loadCommits()
    .map(CommitsReporter.commitsStatsByProjectCategory(GitlabData.loadProjects(), _, interval))
    .map(GitlabInflux.toCommitsStatsByProjectCategoryPoints(_, interval))
    .map(writeChunks)

  def commitsStatsByProjectRoleNamespace(interval: ChronoUnit) = GitlabInflux
    .loadCommits()
    .map(CommitsReporter.commitsStatsByProjectRoleNamespace(GitlabData.loadProjects(), _, interval))
    .map(GitlabInflux.toCommitsStatsByProjectRoleNamespacePoints(_, interval))
    .map(writeChunks)

  def commitsStatsByProjectCategoryNamespace(interval: ChronoUnit) = GitlabInflux
    .loadCommits()
    .map(CommitsReporter.commitsStatsByProjectCategoryNamespace(GitlabData.loadProjects(), _, interval))
    .map(GitlabInflux.toCommitsStatsByProjectCategoryNamespacePoints(_, interval))
    .map(writeChunks)

  def mergeRequestsByProjectRole(targetBranch: String, interval: ChronoUnit) = GitlabInflux
    .loadMergeRequests()
    .map(MergeRequestsReporter.mergeRequestsByProjectRole(_, GitlabData.loadProjects(), targetBranch, interval))
    .map(GitlabInflux.toMergeRequestsByProjectRolePoints(_, interval))
    .map(writeChunks)

  def mergeRequestsByProjectCategory(targetBranch: String, interval: ChronoUnit) = GitlabInflux
    .loadMergeRequests()
    .map(MergeRequestsReporter.mergeRequestsByProjectCategory(_, GitlabData.loadProjects(), targetBranch, interval))
    .map(GitlabInflux.toMergeRequestsByProjectCategoryPoints(_, interval))
    .map(writeChunks)

  def mergeRequestStatsByProject(targetBranch: String, interval: ChronoUnit) = GitlabInflux
    .loadMergeRequests()
    .map(MergeRequestsReporter.mergeRequestStatsByProject(_, GitlabData.loadProjects(), targetBranch, interval))
    .map(GitlabInflux.toMergeRequestStatsByProjectPoints(_, interval))
    .map(writeChunks)

  def committersLifeSpanStats(interval: ChronoUnit) = GitlabInflux
    .loadCommits()
    .map(GitlabReporter.committersLifeSpanStats(_, interval))
    .map(GitlabInflux.toCommittersLifeSpanStatsPiont(_, interval))
    .map(writeChunks)

  private def writeChunks(points: Seq[Point]): Future[Boolean] = {
    val promises = points.sliding(50)
      .map(Influx.db.bulkWrite(_, precision = Precision.MILLISECONDS))

    Future.sequence(promises).map(_.reduce((l, r) => l && r))
  }

  private def writeChunks(points: Array[Point]): Future[Boolean] = writeChunks(points.toSeq)

}
