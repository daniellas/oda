package net.oda.rep.cfd

import java.time.LocalDate
import java.time.temporal.ChronoUnit

import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.{Router, RoutingContext}
import net.oda.Config.{props, reportsLocation}
import net.oda.FileCache.usingCache
import net.oda.FileIO.{lastModified, loadTextContent}
import net.oda.RestApi.apiRoot
import net.oda.data.jira.{Issue, JiraTimestampSerializer, Mappers}
import net.oda.json.LocalDateSerializer
import net.oda.vertx.Paths.{path, variable}
import net.oda.vertx.RequestReaders.{param, params}
import net.oda.vertx.ResponseWriters
import net.oda.{Config, Time}
import org.apache.spark.sql.{Dataset, Row}
import org.apache.spark.sql.functions._
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization

case class CFDReport(data: Seq[Map[String, Any]], aggregates: Seq[Map[String, Any]])

object CFDRest {
  implicit val formats = DefaultFormats + LocalDateSerializer + JiraTimestampSerializer
  val root = "cfd"

  def init(router: Router): Unit = {
    router
      .route(path(root).andThen(variable("projectKey")).apply(apiRoot))
      .method(HttpMethod.GET)
      .blockingHandler(getReport)
  }

  private def readAggregate(param: String) = if (param == "count") count(lit(1)) else sum("estimate")

  def datasetToMaps(dataset: Dataset[Row]) = dataset
    .collect
    .map(r => dataset.columns.foldLeft(Map.empty[String, Any])((acc, i) => acc + (i -> r.getAs[Any](i))))

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
        val aggregate = param(ctx, "aggregate").map(readAggregate).getOrElse(count(lit(1)))
        val start = param(ctx, "start").map(_.toLong).map(Time.toLocalDate).getOrElse(LocalDate.MIN)
        val entryState = param(ctx, "entryState").getOrElse(props.jira.projects(pk).entryState)
        val finalState = param(ctx, "finalState").getOrElse(props.jira.projects(pk).finalState)

        usingCache(
          reportsLocation,
          lastModified(dataLocation),
          "jiraProps",
          props.jira,
          "aggregate", aggregate,
          "interval", interval,
          "item", items,
          "prio", prios,
          "start", start,
          "entryState", entryState,
          "finalState", finalState) {
          () =>
            loadTextContent
              .andThen(Serialization.read[List[Issue]])
              .andThen(_.map(Mappers.jiraIssueToWorkItem(_, props.jira.projects(pk).estimateMapping.get)))
              .andThen(workItems => {
                val data = CFDReporter.generate(
                  pk,
                  start,
                  items.contains,
                  prio => prios.isEmpty || prios.contains(prio),
                  props.jira.projects(pk).referenceFlow,
                  entryState,
                  finalState,
                  props.jira.projects(pk).stateMapping,
                  interval,
                  aggregate,
                  workItems)
                val aggregates = CFDReporter.calculateAggregates(data)

                CFDReport(
                  datasetToMaps(data),
                  datasetToMaps(aggregates))
              })
              .andThen(Serialization.write(_))
              .apply(dataLocation)
        }
      })
      .map(ResponseWriters.body)
      .getOrElse(ResponseWriters.notFound)
      .accept(ctx)
  }

}
