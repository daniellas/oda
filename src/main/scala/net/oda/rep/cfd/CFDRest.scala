package net.oda.rep.cfd

import java.time.LocalDate
import java.time.temporal.ChronoUnit

import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.{Router, RoutingContext}
import net.oda.Config
import net.oda.Config.reportsLocation
import net.oda.FileCache.usingCache
import net.oda.FileIO.{lastModified, loadTextContent}
import net.oda.RestApi.apiRoot
import net.oda.data.jira.{Issue, JiraTimestampSerializer, Mappers}
import net.oda.json.LocalDateSerializer
import net.oda.vertx.Paths.{path, variable}
import net.oda.vertx.RequestReaders.{param, params}
import net.oda.vertx.{RequestReaders, ResponseWriters}
import org.apache.spark.sql.functions._
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization
import org.slf4j.LoggerFactory

import scala.collection.SortedMap

object CFDRest {
  implicit val formats = DefaultFormats + LocalDateSerializer + JiraTimestampSerializer
  val root = "cfd"
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
      .route(path(root).andThen(variable("projectKey")).apply(apiRoot))
      .method(HttpMethod.GET)
      .blockingHandler(getReport)
  }

  def getReport(ctx: RoutingContext): Unit = {
    param(ctx, "projectKey")
      .map(pk => {
        val dataLocation = s"${Config.dataLocation}/jira-issues-${pk}.json"
        val interval = param(ctx, "interval")
          .map(i => i match {
            case "day" => ChronoUnit.DAYS
            case _ => ChronoUnit.WEEKS
          })
          .getOrElse(ChronoUnit.WEEKS)
        val items = params(ctx, "item")
        val prios = params(ctx, "prio")

        usingCache(reportsLocation, lastModified(dataLocation), "interval", interval, "item", items, "prio", prios) {
          () =>
            loadTextContent
              .andThen(Serialization.read[List[Issue]])
              .andThen(_.map(Mappers.jiraIssueToWorkItem(_, _ => Some(0))))
              .andThen(
                CFDReporter.generate(
                  pk,
                  LocalDate.MIN,
                  items.contains,
                  prios.contains,
                  referenceFlow,
                  entryState,
                  finalState,
                  stateMapping,
                  interval,
                  count(lit(1)),
                  _))
              .andThen(report => report.collect.map(r => report.columns.foldLeft(Map.empty[String, Any])((acc, i) => acc + (i -> r.getAs[Any](i)))))
              .andThen(Serialization.write(_)(formats))
              .apply(dataLocation)
        }
      })
      .map(ResponseWriters.body)
      .getOrElse(ResponseWriters.notFound)
      .accept(ctx)
  }

}
