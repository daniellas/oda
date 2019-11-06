package net.oda.it

import java.sql.Timestamp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

import com.paulgoldbaum.influxdbclient.Parameter.Precision
import com.paulgoldbaum.influxdbclient._
import net.oda.Config.props
import net.oda.data.jira.{Issue, JiraTimestampSerializer, Mappers}
import net.oda.db.InfluxDb.db
import net.oda.rep.cfd.{CFDReporter, CFDInfluxDb}
import net.oda.{Config, FileIO, IT}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.{Dataset, Row}
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization
import org.scalatest.FreeSpec

import scala.concurrent.Await
import scala.concurrent.duration._

class CrypCFDInfluxSpec extends FreeSpec {
  implicit val formats = DefaultFormats + JiraTimestampSerializer
  val projectKey = "CRYP"
  val dataLocation = Config.dataLocation

  s"Generate ${projectKey} CFD" taggedAs (IT) in {
    writeToDb(
      generate(
        projectKey,
        props.jira.projects(projectKey).entryState,
        props.jira.projects(projectKey).finalState,
        Seq("Story", "Bug").contains,
        _ => true,
        ChronoUnit.DAYS),
      "All To Do->Done")
  }

  s"Generate ${projectKey} Critical Bugs CFD" taggedAs (IT) in {
    writeToDb(
      generate(
        projectKey,
        props.jira.projects(projectKey).entryState,
        props.jira.projects(projectKey).finalState,
        Seq("Bug").contains,
        "Critical".equals,
        ChronoUnit.DAYS),
      "Critical Bugs")
  }

  def generate(
                projectKey: String,
                entryState: String,
                finalState: String,
                types: String => Boolean,
                priorities: String => Boolean,
                interval: ChronoUnit
              ) = {
    FileIO.loadTextContent
      .andThen(Serialization.read[List[Issue]])
      .andThen(_.map(Mappers.jiraIssueToWorkItem(_, _ => Some(0))))
      .andThen(
        CFDReporter.generate(
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
      .apply(s"${dataLocation}/jira-issues-${projectKey}.json")
  }

  def writeToDb(report: Dataset[Row], qualifier: String): Unit = {
    val points = CFDInfluxDb.toPoints(
      report,
      "oda",
      projectKey,
      qualifier,
      props.jira.projects(projectKey).entryState,
      props.jira.projects(projectKey).finalState)

    Await.result(db.bulkWrite(points, precision = Precision.MILLISECONDS), 100 second)
  }

}
