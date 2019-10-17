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

  val dataLocation = Config.getProp("data.location").getOrElse(() => "./")

  def init(router: Router): Unit = {
    router
      .route(path(root).andThen(variable("projectKey")).andThen(path("download")).apply(apiRoot))
      .method(HttpMethod.POST)
      .blockingHandler(downloadData)
  }

  def downloadData(ctx: RoutingContext): Unit = {
    RequestReaders.param(ctx, "projectKey")
      .map(pk => {
        JiraClient.searchIssues
          .andThen(Serialization.write(_))
          .andThen(FileIO.saveTextContent(s"data/jira-issues-${pk}.json", _))
          .apply(pk)
      })
      .map(ResponseWriters.end)
      .getOrElse(ResponseWriters.notFound)
      .accept(ctx)
  }

}
