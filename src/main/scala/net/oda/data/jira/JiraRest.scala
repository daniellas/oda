package net.oda.data.jira

import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.{Router, RoutingContext}
import net.oda.RestApi.apiRoot
import net.oda.vertx.Paths._
import net.oda.vertx.{RequestReaders, ResponseWriters}
import net.oda.{Config, FileIO}
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization

object JiraRest {
  implicit val formats = DefaultFormats + JiraTimestampSerializer
  val root = "jira"

  def init(router: Router): Unit = {
    router
      .route(path(root).andThen(variable("projectKey")).andThen(path("download")).apply(apiRoot))
      .method(HttpMethod.POST)
      .blockingHandler(downloadData)
    router
      .route(path(root).andThen(path("projects")).apply(apiRoot))
      .method(HttpMethod.GET)
      .blockingHandler(getProjects)
    router
      .route(path(root).andThen(variable("projectKey")).andThen(path("config")).apply(apiRoot))
      .method(HttpMethod.GET)
      .blockingHandler(getConfig)
  }

  def downloadData(ctx: RoutingContext): Unit =
    RequestReaders.param(ctx, "projectKey")
      .map(pk => JiraClient.searchIssues
        .andThen(Serialization.write(_))
        .andThen(FileIO.saveTextContent(s"${Config.dataLocation}/jira-issues-${pk}.json", _))
        .apply(pk)
      )
      .map(_ => ResponseWriters.end)
      .getOrElse(ResponseWriters.notFound)
      .accept(ctx)

  def getProjects(ctx: RoutingContext): Unit =
    ResponseWriters
      .body(Serialization.write(Config.props.jira.projects.keys.toSeq.sorted))
      .accept(ctx)

  def getConfig(ctx: RoutingContext): Unit = RequestReaders.param(ctx, "projectKey")
    .map(pk => Config.props.jira.projects(pk))
    .map(ResponseWriters.body(Serialization.write(_)))
    .getOrElse(ResponseWriters.notFound)
    .accept(ctx)

}
