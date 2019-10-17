package net.oda.data.jira

import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.{Router, RoutingContext}
import net.oda.{Config, FileIO}
import net.oda.RestApi.apiRoot
import net.oda.vertx.Paths.path
import net.oda.vertx.ResponseWriters
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization
import org.slf4j.LoggerFactory

object JiraRest {
  private val log = LoggerFactory.getLogger("JiraRest");
  implicit val formats = DefaultFormats + JiraTimestampSerializer
  val root = "jira"

  val projectKey = "CRYP"
  val dataLocation = Config.getProp("data.location").getOrElse(() => "./")

  def init(router: Router): Unit = {
    router
      .route(path(root).andThen(path("download")).apply(apiRoot))
      .method(HttpMethod.POST)
      .blockingHandler(downloadData)
  }

  def downloadData(ctx: RoutingContext): Unit = {
    log.info("JIRA data download started")

    JiraClient.searchIssues
      .andThen(Serialization.write(_))
      .andThen(FileIO.saveTextContent(s"data/jira-issues-${projectKey}.json", _))
      .apply(projectKey)

    ResponseWriters.end
    log.info("JIRA data download completed")
  }

}
