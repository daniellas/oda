package net.oda.rep

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import com.typesafe.scalalogging.Logger
import net.oda.cfd.CfdReporter
import net.oda.{Config, FileIO, IT, Spark, Time}
import net.oda.gitlab.{GitlabClient, GitlabData, GitlabInflux, GitlabReporter}
import net.oda.jira.{JiraData, JiraReporter}
import net.oda.rep.ReportsGenerator.{jiraCountByTypePriority, jiraCountCfd, jiraCountDistinctAuthors, jiraEstimateCfd, teamProductivityFactor, workItemsChangelog, workItemsDuration}
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization
import org.scalatest.FreeSpec

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import net.oda.Spark.session.implicits._
import net.oda.workitem.WorkItems
import org.apache.spark.sql.SaveMode
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions._

class SingleReportsSpec extends FreeSpec {
  val log = Logger(classOf[SingleReportsSpec])
  val months = 10 * 12;
  implicit val jsonFormats = DefaultFormats
  val stateMapping = Map("Invalid" -> "Done", "IN REVIW" -> "In Review")
  val referenceFlow = Map(
    "In Progress" -> 2,
    "In Review" -> 3,
    "Ready to test" -> 4,
    "In testing" -> 5,
    "Done" -> 6)
  val projectKey = "CRYP"
  val entryState = "In Progress"
  val finalState = "Done"

  s"Generate" taggedAs (IT) in {
    val workItems = JiraData
      .loadAsWorkItems(Config.props.jira.projects(projectKey).estimateMapping.get)
      .apply(JiraData.location(projectKey));

    val interval = ChronoUnit.WEEKS
    val range = udf(Time.range(interval, _, _))

    WorkItems.flatten(workItems)
      .toDF
      .orderBy('id, 'statusCreated)
      .select('id, 'statusCreated, 'statusName)
      .withColumn(
        "statusChanged",
        first('statusCreated)
          .over(
            Window
              .orderBy('statusCreated)
              .partitionBy('id)
              .rowsBetween(Window.currentRow + 1, Window.unboundedFollowing)
          )
      )
      .filter('statusChanged.isNotNull)
      .withColumn("createdWeek", Spark.toIntervalStart(interval)('statusCreated))
      .withColumn("changedWeek", Spark.toIntervalStart(interval)('statusChanged))
      .withColumn("range", range('createdWeek, 'changedWeek))
      .select('statusName, explode('range).as("week"))
      .groupBy('week, 'statusName)
      .count()
      .orderBy('week, 'statusName)
      .show(100)

  }

}
