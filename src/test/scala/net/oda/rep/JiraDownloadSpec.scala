package net.oda.rep

import com.typesafe.scalalogging.Logger
import net.oda.jira.{JiraClient, JiraTimestampSerializer}
import net.oda.{Config, FileIO, IT}
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization
import org.scalatest.FreeSpec

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class JiraDownloadSpec extends FreeSpec {
  val log = Logger(classOf[JiraDownloadSpec])
  implicit val formats = DefaultFormats + JiraTimestampSerializer

  s"Download issues" taggedAs (IT) in {
    Config.props.jira.projects.keys.foreach(download)
  }

  def download(projectKey: String) = {
    log.info("Downloading {} JIRA data", projectKey)

    val res = JiraClient.searchIssues(projectKey)
      .map(Serialization.write(_)(formats))

    res.onComplete(_.foreach(FileIO.saveTextContent(s"${Config.dataLocation}/jira-issues-${projectKey}.json", _: String)))

    Await.result(res, 1 minute)
    log.info("{} JIRA data downloaded", projectKey)
  }
}
