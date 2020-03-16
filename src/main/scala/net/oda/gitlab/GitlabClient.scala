package net.oda.gitlab

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

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

case class Project(id: Int, name: String, name_with_namespace: String) {
  def namespace() = name_with_namespace.substring(0, name_with_namespace.length - name.trim.length - 3)
}

case class Stats(additions: Int, deletions: Int, total: Int)

case class Commit(
                   id: String,
                   short_id: String,
                   title: String,
                   message: String,
                   created_at: ZonedDateTime,
                   committer_email: String,
                   stats: Stats) {
  def mapCommitterEmail(mapper: String => String) =
    Commit(
      this.id,
      this.short_id,
      this.title,
      this.message,
      this.created_at,
      mapper(this.committer_email),
      this.stats
    )
}

case class Author(id: Int, username: String)

case class MergeRequest(
                         id: Int,
                         iid: Int,
                         state: String,
                         created_at: ZonedDateTime,
                         author: Author,
                         merged_at: Option[ZonedDateTime],
                         closed_at: Option[ZonedDateTime],
                         user_notes_count: Int,
                         project_id: Int,
                         source_branch: String,
                         target_branch: String
                       )

object GitlabClient {
  val log = Logger("gitlab-client")
  implicit val formats = DefaultFormats + ZondedDateTimeSerializer
  val dateTimeFormatter = DateTimeFormatter.ofPattern("yyy-MM-dd'T'HH:mm:ssZ")

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

  def getCommits(projectId: Int, ref: String, since: ZonedDateTime, firstParent: Boolean): Future[Seq[Commit]] = {
    getPages(
      restClient
        .resource(
          "/projects/%s/repository/commits?per_page=100&ref_name=%s&since=%s&with_stats=true&first_parent=%s&page=%s",
          projectId,
          ref,
          since.format(dateTimeFormatter),
          firstParent,
          _)
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

  def getMergeRequests(since: ZonedDateTime) = getPages(
    restClient
      .resource(
        "/merge_requests?per_page=100&scope=all&created_after=%s&page=%s",
        since.format(dateTimeFormatter),
        _)
      .get()
      .execute()
      .map(_.map(Serialization.read[Seq[MergeRequest]])),
    Seq.empty,
    1)

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
