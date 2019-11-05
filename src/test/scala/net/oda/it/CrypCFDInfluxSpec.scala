package net.oda.it

import java.sql.Timestamp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

import com.paulgoldbaum.influxdbclient.Parameter.Precision
import com.paulgoldbaum.influxdbclient._
import net.oda.Config.props
import net.oda.data.jira.{Issue, JiraTimestampSerializer, Mappers}
import net.oda.rep.cfd.CFDReporter
import net.oda.{Config, FileIO, IT}
import org.apache.spark.sql.{Dataset, Row}
import org.apache.spark.sql.functions._
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization
import org.scalatest.FreeSpec

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class CrypCFDInfluxSpec extends FreeSpec {
  implicit val formats = DefaultFormats + JiraTimestampSerializer
  val projectKey = "CRYP"
  val dataLocation = Config.dataLocation
  val server = InfluxDB.connect("localhost", 8086)
  val db = server.selectDatabase("oda")

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

  def writeToDb(report: Dataset[Row], qualifier: String): Unit = {
    val points = report
      .collect
      .map(r => Point("cfd", r.getAs[Timestamp](CFDReporter.timeCol).getTime)
        .addTag("project", projectKey)
        .addTag("qualifier", qualifier)
        .addField(CFDReporter.ctCol, r.getAs[Long](CFDReporter.ctCol))
        .addField(CFDReporter.thCol, r.getAs[Double](CFDReporter.thCol))
        .addField(CFDReporter.wipCol, r.getAs[Long](CFDReporter.wipCol))
        .addField(props.jira.projects(projectKey).entryState, r.getAs[Long](props.jira.projects(projectKey).entryState))
        .addField(props.jira.projects(projectKey).finalState, r.getAs[Long](props.jira.projects(projectKey).finalState))
      )

    Await.result(db.bulkWrite(points, precision = Precision.MILLISECONDS), 100 second)
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
}
