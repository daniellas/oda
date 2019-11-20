package net.oda.it

import java.time.LocalDate
import java.time.temporal.ChronoUnit

import com.paulgoldbaum.influxdbclient.Parameter.Precision
import net.oda.Config.props
import net.oda.data.jira.{Issue, JiraTimestampSerializer, Mappers}
import net.oda.db.InfluxDb.db
import net.oda.rep.cfd.{CFDInfluxDb, CFDReporter}
import net.oda.{Config, FileIO, IT}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.{Dataset, Row}
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization
import org.scalatest.FreeSpec

import scala.concurrent.Await
import scala.concurrent.duration._

class CFDInfluxSpec extends FreeSpec {
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
      "All To Do->Done")
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
      "Critical Bugs")
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
      "All To Do->Done")
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

  def writeToDb(report: Dataset[Row], projectKey: String, qualifier: String): Unit = {
    val points = CFDInfluxDb.toPoints(
      report,
      "cfd",
      projectKey,
      qualifier,
      props.jira.projects(projectKey).entryState,
      props.jira.projects(projectKey).finalState)

    Await.result(db.bulkWrite(points, precision = Precision.MILLISECONDS), 100 second)
  }

}
