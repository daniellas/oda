package net.oda.gitlab

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import com.typesafe.scalalogging.Logger
import io.netty.handler.codec.http.HttpResponseStatus
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

case class Project(id: Int, name: String, name_with_namespace: String)

case class Stats(additions: Int, deletions: Int, total: Int)

case class Commit(
                   id: String,
                   short_id: String,
                   title: String,
                   message: String,
                   created_at: ZonedDateTime,
                   committer_email: String,
                   stats: Stats)

object GitlabClient {
  val log = Logger("gitlab-client")
  implicit val formats = DefaultFormats + ZondedDateTimeSerializer
  val restClient = RestClient.using(VertxHttpExecutor.of(VertxServices.vertx, VertxServices.httpClient, (m, e) => log.error(m, e), m => log.debug(m)))
    .service(Config.props.gitlab.apiUrl)
    .defaultHeaders(Map(
      "Private-Token" -> Seq(Config.props.gitlab.token),
      HttpHeaders.CONTENT_TYPE -> Seq(ContentType.APPLICATION_JSON.toString)))

  def getProjects(): Future[Seq[Project]] = getPages(
    restClient
      .resource("/projects?archived=false&per_page=100&page=%s", _)
      .get()
      .execute()
      .map(_.map(Serialization.read[Seq[Project]])),
    Seq.empty,
    1
  )

  def getNamespaces(): Future[Seq[Namespace]] = getPages(
    restClient
      .resource("/namespaces?per_page=100&page=%s", _)
      .get()
      .execute()
      .map(_.map(Serialization.read[Seq[Namespace]])),
    Seq.empty,
    1)

  def getProject(path: String): Future[Project] =
    restClient
      .resource("/projects/%s", path)
      .get()
      .execute()
      .flatMap(_.body
        .map(Serialization.read[Project])
        .map(Future.successful)
        .getOrElse(emptyBodyFailure()))

  val dateTimeFormatter = DateTimeFormatter.ofPattern("yyy-MM-dd'T'HH:mm:ssZ")

  def getCommits(projectId: Int, ref: String, since: ZonedDateTime): Future[Seq[Commit]] = {
    getPages(
      restClient
        .resource("/projects/%s/repository/commits?per_page=100&ref_name=%s&since=%s&with_stats=true&page=%s", projectId, ref, since.format(dateTimeFormatter), _)
        .get()
        .execute()
        .map(_.map(Serialization.read[Seq[Commit]]))
        .recover { case _: Throwable => Response(200, "", Map.empty, Some(Seq.empty[Commit])) },
      Seq.empty,
      1)
  }

  def getCommit(projectId: Int, sha: String): Future[Commit] = restClient
    .resource("/projects/%s/repository/commits/%s", projectId, sha)
    .get()
    .execute()
    .flatMap(_.body
      .map(Serialization.read[Commit])
      .map(Future.successful)
      .getOrElse(emptyBodyFailure()))

  private def emptyBodyFailure[A](): Future[A] = Future.failed[A](new NoSuchElementException())

  private def getPages[A](fn: Int => Future[Response[Seq[A]]], res: Seq[A], page: Int): Future[Seq[A]] = {
    fn.apply(page)
      .flatMap(r =>
        r.headers
          .get("X-Next-Page")
          .flatMap(_.headOption)
          .filterNot("".equals)
          .map(_.toInt)
          .map(getPages(fn, r.body.map(res ++ _).getOrElse(res), _))
          .getOrElse(Promise.successful(r.body.map(res ++ _).getOrElse(res)).future)
      )
  }

}
