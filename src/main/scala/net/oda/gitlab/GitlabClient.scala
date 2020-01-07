package net.oda.gitlab

import java.time.ZonedDateTime

import com.typesafe.scalalogging.Logger
import net.oda.Config
import net.oda.json.ZondedDateTimeSerializer
import net.oda.rest.client.{Response, RestClient, VertxHttpExecutor}
import net.oda.vertx.VertxServices
import org.apache.http.HttpHeaders
import org.apache.http.entity.ContentType
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

case class Namespace(id: String, name: String, path: String, full_path: String, parent_id: Option[Int])

case class Project(id: Int, name: String)

case class Commit(id: String, title: String, message: String, created_at: ZonedDateTime)

object GitlabClient {
  val log = Logger("gitlab-client")
  implicit val formats = DefaultFormats + ZondedDateTimeSerializer
  val restClient = RestClient.using(VertxHttpExecutor.of(VertxServices.vertx, VertxServices.httpClient, (m, e) => log.error(m, e), m => log.debug(m)))
    .service(Config.props.gitlab.apiUrl)
    .defaultHeaders(Map(
      "Private-Token" -> Seq(Config.props.gitlab.token),
      HttpHeaders.CONTENT_TYPE -> Seq(ContentType.APPLICATION_JSON.toString)))

  def getNamespaces(): Future[Seq[Namespace]] = getPages(
    p => restClient
      .resource("/namespaces?per_page=100&page=%s", p)
      .get
      .execute
      .map(_.map(Serialization.read[Seq[Namespace]])),
    Seq.empty,
    1)

  def getProject(path: String): Future[Option[Project]] =
    restClient
      .resource("/projects/%s", path)
      .get
      .execute
      .map(_.body.map(Serialization.read[Project]))

  def getCommits(projectId: Int, ref: String): Future[Seq[Commit]] = getPages(
    p => restClient
      .resource("/projects/%s/repository/commits?per_page=100&ref_name=%s&page=%s", projectId, ref, p)
      .get
      .execute
      .map(_.map(Serialization.read[Seq[Commit]])),
    Seq.empty,
    1)

  private def getPages[A](fn: Int => Future[Response[Seq[A]]], res: Seq[A], page: Int): Future[Seq[A]] = {
    fn.apply(page)
      .flatMap(r =>
        r.headers
          .get("X-Next-Page")
          .flatMap(_.headOption)
          .filterNot("".equals)
          .map(_.toInt)
          .map(getPages(fn, r.body.map(res ++ _).getOrElse(Seq.empty), _))
          .getOrElse(Promise.successful(res).future)
      )
  }

}
