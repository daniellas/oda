package net.oda.it

import java.time.LocalDate
import java.time.temporal.ChronoUnit

import com.paulgoldbaum.influxdbclient.Parameter.Precision
import net.oda.Config.props
import net.oda.cfd.{CfdInflux, CfdReporter}
import net.oda.influx.InfluxDb.db
import net.oda.jira.{JiraData, JiraTimestampSerializer, Mappers}
import net.oda.{Config, IT}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.{Dataset, Row}
import org.json4s.DefaultFormats
import org.scalatest.FreeSpec

import scala.concurrent.Await
import scala.concurrent.duration._

class CfdReportsSpec extends FreeSpec {
  implicit val formats = DefaultFormats + JiraTimestampSerializer
  val dataLocation = Config.dataLocation

  s"Generate CRYP CFD" taggedAs (IT) in {
    writeToDb(
      generate(
        "CRYP",
        props.jira.projects("CRYP").entryState,
        props.jira.projects("CRYP").finalState,
        Seq("Story", "Bug").contains,
        _ => true,
        ChronoUnit.DAYS),
      "CRYP",
      "All",
      ChronoUnit.DAYS)
  }

  s"Generate weekly CRYP CFD" taggedAs (IT) in {
    writeToDb(
      generate(
        "CRYP",
        props.jira.projects("CRYP").entryState,
        props.jira.projects("CRYP").finalState,
        Seq("Story", "Bug").contains,
        _ => true,
        ChronoUnit.WEEKS),
      "CRYP",
      "All",
      ChronoUnit.WEEKS)
  }

  s"Generate CRYP Critical Bugs CFD" taggedAs (IT) in {
    writeToDb(
      generate(
        "CRYP",
        props.jira.projects("CRYP").entryState,
        props.jira.projects("CRYP").finalState,
        Seq("Bug").contains,
        "Critical".equals,
        ChronoUnit.DAYS),
      "CRYP",
      "Critical bugs",
      ChronoUnit.DAYS)
  }

  s"Generate AW CFD" taggedAs (IT) in {
    writeToDb(
      generate(
        "AW",
        props.jira.projects("AW").entryState,
        props.jira.projects("AW").finalState,
        Seq("Story", "Bug").contains,
        _ => true,
        ChronoUnit.DAYS),
      "AW",
      "All",
      ChronoUnit.DAYS)
  }

  def generate(
                projectKey: String,
                entryState: String,
                finalState: String,
                types: String => Boolean,
                priorities: String => Boolean,
                interval: ChronoUnit
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
      .apply(JiraData.location(projectKey))
  }

  def writeToDb(report: Dataset[Row], projectKey: String, qualifier: String, interval: ChronoUnit): Unit = {
    val points = CfdInflux.toPoints(
      report,
      projectKey,
      qualifier,
      props.jira.projects(projectKey).entryState,
      props.jira.projects(projectKey).finalState,
      interval.name)

    Await.result(db.bulkWrite(points, precision = Precision.MILLISECONDS), 100 second)
  }

}
