package net.oda.jira

import java.util.Base64

import com.typesafe.scalalogging.Logger
import net.oda.Config
import net.oda.rest.client.{RestClient, VertxHttpExecutor}
import net.oda.vertx.VertxServices
import org.apache.http.HttpHeaders
import org.json4s.jackson.Serialization
import org.json4s.{DefaultFormats, FieldSerializer}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

object JiraClient {
  val log = Logger("jira-client")

  implicit val formats = DefaultFormats + JiraTimestampSerializer +
    FieldSerializer[ChangeItem](
      FieldSerializer.renameTo("toStr", "toString"),
      FieldSerializer.renameFrom("toString", "toStr")
    )

  val jiraAuthHeader = Config.props.jira.user + ":" + Config.props.jira.apiKey
  val jiraHeaders = Map(HttpHeaders.AUTHORIZATION -> Seq("Basic " + Base64.getEncoder.encodeToString(jiraAuthHeader.getBytes)))
  val restClient = RestClient.using(VertxHttpExecutor.of(VertxServices.vertx, VertxServices.httpClient, (m, e) => log.error(m, e), m => log.debug(m)))
    .service(Config.props.jira.apiUrl)
    .defaultHeaders(jiraHeaders)

  val expand = "changelog,-schema,-editmeta"
  val fields = "resolution,summary,reporter,created,resolutiondate,status,priority,project,issuetype,size"
  val maxResults = 100

  private def getIsses(project: String, take: Int, issues: JiraIssues): Future[JiraIssues] = {
    log.info("Downloading {} issues {} to {}", project, issues.startAt, issues.startAt + maxResults)

    restClient
      .resource("/search?jql=%s&expand=%s&fields=%s&startAt=%s&maxResults=%s", s"project=$project", expand, fields, issues.startAt, maxResults)
      .get
      .execute
      .map(r => {
        log.info("Response status: {}", r.statusCode)
        r
      })
      .filter(_.statusCode == 200)
      .map(_.body.get)
      .map(Serialization.read[JiraIssues])
      .flatMap(i => if (i.total > issues.startAt && (issues.startAt < take || take == -1)) getIsses(project, take, new JiraIssues(i.startAt + maxResults, maxResults, i.total, issues.issues ::: i.issues)) else Promise.successful(issues).future)
  }

  def searchIssues(project: String, take: Int): Future[List[Issue]] = getIsses(project, take, JiraIssues.empty).map(_.issues)

  def searchIssues(project: String): Future[List[Issue]] = searchIssues(project, -1)

}