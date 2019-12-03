package net.oda.rep

import java.time.LocalDate
import java.time.temporal.ChronoUnit

import com.paulgoldbaum.influxdbclient.Parameter.Precision
import net.oda.Config
import net.oda.cfd.{CfdInflux, CfdReporter}
import net.oda.influx.InfluxDb.db
import net.oda.jira.{JiraData, JiraInflux, JiraReporter}

import scala.concurrent.Future

object ReportsGenerator {

  def workItemsChangelog(projectKey: String, interval: ChronoUnit) = {
    JiraData
      .loadAsWorkItems(Config.props.jira.projects(projectKey).estimateMapping.get)
      .andThen(JiraReporter.workItemsChangeLog(_, interval))
      .andThen(JiraInflux.workItemsChangelog(_, projectKey, interval.name))
      .andThen(db.bulkWrite(_, precision = Precision.MILLISECONDS))
      .apply(JiraData.location(projectKey))
  }

  def jiraCountByTypePriority(projectKey: String, interval: ChronoUnit, stateMapping: Map[String, String]) = {
    JiraData
      .loadAsWorkItems(Config.props.jira.projects(projectKey).estimateMapping.get)
      .andThen(JiraReporter.countByTypePriority(_, stateMapping, interval))
      .andThen(JiraInflux.countByTypePriorityPoints(_, projectKey, interval.name))
      .andThen(db.bulkWrite(_, precision = Precision.MILLISECONDS))
      .apply(JiraData.location(projectKey))
  }

  def jiraCountDistinctAuthors(projectKey: String, interval: ChronoUnit, stateFilter: String => Boolean, qualifier: String) = {
    JiraData
      .loadAsWorkItems(Config.props.jira.projects(projectKey).estimateMapping.get)
      .andThen(JiraReporter.countDistinctAuthor(_, stateFilter, interval))
      .andThen(JiraInflux.countDistinctAuthorsPoints(_, projectKey, interval.name, qualifier))
      .andThen(db.bulkWrite(_, precision = Precision.MILLISECONDS))
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
                  ): Future[Boolean] = {
    JiraData
      .loadAsWorkItems(Config.props.jira.projects(projectKey).estimateMapping.get)
      .andThen(
        CfdReporter
          .generate(projectKey, LocalDate.MIN, types, priorities, referenceFlow, entryState, finalState, stateMapping, interval, CfdReporter.countAggregate, _))
      .andThen(CfdInflux.toPointsOfInts("cfd-count", _, projectKey, qualifier, entryState, finalState, interval.name))
      .andThen(db.bulkWrite(_, precision = Precision.MILLISECONDS))
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
                     ): Future[Boolean] = {
    JiraData
      .loadAsWorkItems(Config.props.jira.projects(projectKey).estimateMapping.get)
      .andThen(
        CfdReporter
          .generate(projectKey, LocalDate.MIN, types, priorities, referenceFlow, entryState, finalState, stateMapping, interval, CfdReporter.sumEstimateAggregate, _))
      .andThen(CfdInflux.toPointsOfDecimals("cfd-estimate", _, projectKey, qualifier, entryState, finalState, interval.name))
      .andThen(db.bulkWrite(_, precision = Precision.MILLISECONDS))
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
      .andThen(db.bulkWrite(_, precision = Precision.MILLISECONDS))
      .apply(JiraData.location(projectKey))
  }

}
