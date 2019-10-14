package net.oda.rep.cfd

import java.time.LocalDate
import java.time.temporal.ChronoUnit

import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.{Router, RoutingContext}
import net.oda.RestApi.apiRoot
import net.oda.data.jira.{Issue, JiraTimestampSerializer, Mappers}
import net.oda.json.LocalDateSerializer
import net.oda.vertx.Paths.path
import net.oda.vertx.{RequestReaders, ResponseWriters}
import net.oda.{Config, IO}
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization
import org.apache.spark.sql.functions._

import scala.collection.SortedMap

object CFDRest {
  implicit val formats = DefaultFormats + LocalDateSerializer + JiraTimestampSerializer
  val root = "cfd"

  val projectKey = "CRYP"
  val dataLocation = Config.getProp("data.location").getOrElse(() => "./")
  val referenceFlow = SortedMap(
    "Backlog" -> -1,
    "Upcoming" -> 0,
    "To Do" -> 1,
    "In Progress" -> 2,
    "In Review" -> 3,
    "Ready to test" -> 4,
    "In testing" -> 5,
    "Done" -> 6
  )
  val entryState = "To Do"
  val finalState = "Done"
  val stateMapping = Map("Invalid" -> "Done")

  def init(router: Router): Unit = {
    router
      .route(path(root).apply(apiRoot))
      .method(HttpMethod.GET)
      .blockingHandler(getReport)
  }

  def getReport(ctx: RoutingContext): Unit = {
    val interval = RequestReaders.param(ctx, "interval")
      .map(i => i match {
        case "day" => ChronoUnit.DAYS
        case _ => ChronoUnit.WEEKS
      })
      .getOrElse(ChronoUnit.WEEKS);

    IO.loadTextContent
      .andThen(Serialization.read[List[Issue]])
      .andThen(_.map(Mappers.jiraIssueToWorkItem(_, _ => Some(0))))
      .andThen(
        CFDReporter.generate(
          projectKey,
          LocalDate.MIN,
          RequestReaders.params(ctx, "item").contains,
          _ => true,
          referenceFlow,
          entryState,
          finalState,
          stateMapping,
          interval,
          count(lit(1)),
          _))
      .andThen(report => report.collect.map(r => report.columns.foldLeft(Map.empty[String, Any])((acc, i) => acc + (i -> r.getAs[Any](i)))))
      .andThen(Serialization.write(_)(formats))
      .andThen(ResponseWriters.body)
      .apply(s"${dataLocation}/jira-issues-${projectKey}.json")
      .accept(ctx)
  }

}
