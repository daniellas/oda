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
import net.oda.{Config, FileIO, Encoding}
import org.apache.spark.sql.functions._
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization
import org.slf4j.LoggerFactory

import scala.collection.SortedMap

object CFDRest {
  private val log = LoggerFactory.getLogger("CFDRest");
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
    val dataPath = s"${dataLocation}/jira-issues-${projectKey}.json"
    val interval = RequestReaders.param(ctx, "interval")
      .map(i => i match {
        case "day" => ChronoUnit.DAYS
        case _ => ChronoUnit.WEEKS
      })
      .getOrElse(ChronoUnit.WEEKS);
    val items = RequestReaders.params(ctx, "item")
    val prios = RequestReaders.params(ctx, "prio")
    val cachedFilePath = Config.getProp("reports.location")
      .map(_ + "/" + Encoding.encodeFilePath(Seq(FileIO.lastModified(dataPath), interval, "item", items, "prios", prios)))
      .map(_ + ".json")
      .get

    val resp = FileIO.tryLoadTextContent(cachedFilePath)
      .getOrElse(FileIO.loadTextContent
        .andThen(Serialization.read[List[Issue]])
        .andThen(_.map(Mappers.jiraIssueToWorkItem(_, _ => Some(0))))
        .andThen(
          CFDReporter.generate(
            projectKey,
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
        .apply(s"${dataLocation}/jira-issues-${projectKey}.json")
      )

    FileIO.saveTextContent(cachedFilePath, resp)

    ResponseWriters.body(resp).accept(ctx)
  }

}
