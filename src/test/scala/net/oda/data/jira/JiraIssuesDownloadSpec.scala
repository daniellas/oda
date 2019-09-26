package net.oda.data.jira

import net.oda.json.JsonSer
import net.oda.{Config, IO}
import org.json4s.DefaultFormats
import org.scalatest.FreeSpec

class JiraIssuesDownloadSpec extends FreeSpec {

  implicit val formats = DefaultFormats + JiraTimestampSerializer

  "Jira issues shoul be downloaded and saved" in {
    val projectKey = "CRYP"
    val dataLocation = Config.getProp("data.location").getOrElse(() => "./")

    JiraClient.searchIssues
      .andThen(JsonSer.writeAsString(formats, _))
      .andThen(IO.saveTextContent(s"${dataLocation}/${projectKey}-jira-issues.json", _: String))
      .apply(projectKey)
  }
}