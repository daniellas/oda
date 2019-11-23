package net.oda.it

import java.time.LocalDate
import java.time.temporal.ChronoUnit

import com.paulgoldbaum.influxdbclient.Parameter.Precision
import com.typesafe.scalalogging.Logger
import net.oda.Config.props
import net.oda.cfd.{CfdInfluxDb, CfdReporter}
import net.oda.influx.InfluxDb.db
import net.oda.jira.{JiraData, JiraInfluxDb, JiraReporter, Mappers}
import net.oda.{Config, IT}
import org.apache.spark.sql.functions.{count, lit}
import org.scalatest.FreeSpec

import scala.concurrent.Await
import scala.concurrent.duration._

case class CfdSpec(qualifier: String, typesFilter: String => Boolean, priosFilter: String => Boolean)

class ReportsGeneratorSpec extends FreeSpec {
  val log = Logger(classOf[ReportsGeneratorSpec])
  val intervals = Seq(ChronoUnit.WEEKS, ChronoUnit.DAYS)
  val cfdSpecs = Seq(
    CfdSpec("All", Seq("Story", "Bug").contains, _ => true),
    CfdSpec("Critical bugs", "Bug".equals, "Critical".equals))

  s"Generate reports" taggedAs (IT) in {
    Config.props.jira.projects.foreach(p => {
      intervals.foreach(i => {
        jiraCountByTypePriority(p._1, i)
        cfdSpecs.foreach(s => jiraCfd(p._1, p._2.entryState, p._2.finalState, s.typesFilter, s.priosFilter, i, s.qualifier))
      })
    })
  }

  def jiraCountByTypePriority(projectKey: String, interval: ChronoUnit) = {
    JiraData
      .load
      .andThen(_.map(Mappers.jiraIssueToWorkItem(_, _ => Some(0))))
      .andThen(JiraReporter.countByTypePriority(_, Config.props.jira.projects(projectKey).stateMapping, interval))
      .andThen(JiraInfluxDb.countByTypePriorityPoints(_, projectKey, interval.name))
      .andThen(points => Await.result(db.bulkWrite(points, precision = Precision.MILLISECONDS), 100 second))
      .apply(JiraData.location(projectKey))
  }

  def jiraCfd(
               projectKey: String,
               entryState: String,
               finalState: String,
               types: String => Boolean,
               priorities: String => Boolean,
               interval: ChronoUnit,
               qualifier: String
             ) = {
    JiraData
      .load
      .andThen(_.map(Mappers.jiraIssueToWorkItem(_, _ => Some(0))))
      .andThen(
        CfdReporter.generate(
          projectKey,
          LocalDate.MIN,
          types,
          priorities,
          props.jira.projects(projectKey).referenceFlow,
          entryState,
          finalState,
          props.jira.projects(projectKey).stateMapping,
          interval,
          count(lit(1)),
          _))
      .andThen(
        CfdInfluxDb.toPoints(
          _,
          projectKey,
          qualifier,
          props.jira.projects(projectKey).entryState,
          props.jira.projects(projectKey).finalState,
          interval.name))
      .andThen(points => Await.result(db.bulkWrite(points, precision = Precision.MILLISECONDS), 100 second))
      .apply(JiraData.location(projectKey))
  }

}
