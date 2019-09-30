package net.oda.data.jira

import java.util.{Base64, Collections, HashMap, Map}

import com.empirica.rest.client.vertx.VertxAsyncHttpExecutor
import com.empirica.rest.client.{AsyncCombinators, Headers, RestClient}
import net.oda.Config
import net.oda.data.Rest
import net.oda.vertx.VertxServices
import org.apache.http.HttpHeaders
import org.json4s.jackson.Serialization
import org.json4s.{DefaultFormats, FieldSerializer}

object JiraClient {
  implicit val formats = DefaultFormats + JiraTimestampSerializer +
    FieldSerializer[ChangeItem](
      FieldSerializer.renameTo("toStr", "toString"),
      FieldSerializer.renameFrom("toString", "toStr")
    )

  val jiraAuthHeader = Config
    .getProp("jira.user")
    .map(_ + ":")
    .flatMap(h => Config.getProp("jira.apiKey").map(i => h + i))
    .get

  val jiraHeaders: Map[String, java.util.List[String]] = new HashMap();

  jiraHeaders.put(HttpHeaders.AUTHORIZATION, Collections.singletonList("Basic " + Base64.getEncoder.encodeToString(jiraAuthHeader.getBytes)))

  val restClient = RestClient.using(VertxAsyncHttpExecutor.of(VertxServices.httpClient))
    .service(Config.getProp("jira.apiUrl").get)
    .defaultHeaders(Headers.combine(Rest.jsonHeaders, jiraHeaders))

  val expand = "changelog,-schema,-editmeta"
  val fields = "resolution,summary,reporter,created,resolutiondate,status,priority,project,issuetype"

  private def getIssuesPage(project: String, startAt: Int = 0): List[JiraIssues] = {
    val body = restClient.resource("/search?jql=%s&expand=%s&fields=%s&startAt=%s&maxResults=100", s"project = $project", expand, fields, startAt.toString)
      .getDefault
      .execute
      .toCompletableFuture
      .get
      .getBody

    val issues = Serialization.read[JiraIssues](body)

    if (issues.total > startAt) {
      getIssuesPage(project, startAt + issues.maxResults) ::: List(issues)
    } else {
      List(issues)
    }
  }

  val searchIssues = (project: String) => getIssuesPage(project).flatMap(_.issues)

}