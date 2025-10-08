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

  implicit val formats = DefaultFormats +
    JiraTimestampSerializer +
    FieldSerializer[ChangeItem](FieldSerializer.renameTo("toStr", "toString"), FieldSerializer.renameFrom("toString", "toStr")) +
    JiraDateSerializer

  val jiraAuthHeader = Config.props.jira.user + ":" + Config.props.jira.apiKey
  val jiraHeaders = Map(HttpHeaders.AUTHORIZATION -> Seq("Basic " + Base64.getEncoder.encodeToString(jiraAuthHeader.getBytes)))
  val restClient = RestClient.using(VertxHttpExecutor.of(VertxServices.vertx, VertxServices.httpClient, (m, e) => log.error(m, e), m => log.debug(m)))
    .service(Config.props.jira.apiUrl)
    .defaultHeaders(jiraHeaders)

  val expand = "changelog,-schema,-editmeta"
  val fields = "resolution,summary,reporter,created,resolutiondate,status,priority,project,issuetype,size,customfield_10035"
  val maxResults = 100

  private def getIsses(project: String, take: Int, issues: JiraIssues): Future[JiraIssues] = {
    log.info("Downloading {} issues, {} completed", project, issues.issues.length)

    issues.nextPageToken
      .map(token => {
        //        log.info("Next page token {}", token)
        restClient
          .resource(
            "/search/jql?jql=%s&expand=%s&fields=%s&maxResults=%s&nextPageToken=%s",
            s"project=$project", expand, fields, maxResults, token)
      })
      .getOrElse({
        restClient
          .resource(
            "/search/jql?jql=%s&expand=%s&fields=%s&maxResults=%s",
            s"project=$project", expand, fields, maxResults)
      })
      .get
      .execute
      .map(r => {
        //        log.info("Response status: {} {} {}", r.statusCode, r.statusLine, r.body)
        r
      })
      .filter(_.statusCode == 200)
      .map(_.body.get)
      .map(Serialization.read[JiraIssues])
      .map(r => {
        //        log.info("isLast: {}", r.isLast)
        r
      })
      .flatMap(i => {
        val res = new JiraIssues(i.isLast, i.nextPageToken, issues.issues ::: i.issues)

        if (!i.isLast) {
          getIsses(project, take, res)
        } else {
          Promise.successful(res).future
        }
      })
  }

  def searchIssues(project: String, take: Int): Future[List[Issue]] = getIsses(project, take, JiraIssues.empty).map(_.issues)

  def searchIssues(project: String): Future[List[Issue]] = searchIssues(project, -1)

  def getProjectVersions(project: String): Future[Seq[ProjectVersions]] = restClient.resource("/project/%s/versions", project)
    .get
    .execute
    .filter(_.statusCode == 200)
    .map(_.body.get)
    .map(Serialization.read[Seq[ProjectVersions]])
}