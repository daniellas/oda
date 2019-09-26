package net.oda.rep

import java.time.ZonedDateTime

import net.oda.data.jira.{Issue, JiraTimestampSerializer}
import net.oda.model.WorkItemStatusHistory
import net.oda.{Config, IO, Mappers}
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization
import org.scalatest.{FlatSpec, Matchers}
import Mappers._

class CFDSpec extends FlatSpec with Matchers {
  implicit val formats = DefaultFormats + JiraTimestampSerializer

  it should "create flow description" in {
    CFDReporter.flowDesc(List("1", "2")) should equal("/1/2")
  }

  it should "strip flow history" in {
    val now = ZonedDateTime.now();

    CFDReporter.normalizeFlow(
      List(
        WorkItemStatusHistory(now.plusHours(0), "backlog"),
        WorkItemStatusHistory(now.plusHours(1), "todo"),
        WorkItemStatusHistory(now.plusHours(2), "done"))) should contain
    (
      WorkItemStatusHistory(now.plusHours(1), "todo"),
      WorkItemStatusHistory(now.plusHours(2), "done"))
  }

  it should "generate CFD from JIRA issues" in {
    val dataLocation = Config.getProp("data.location").getOrElse(() => "./")
    val projectKey = "CRYP"

    IO.loadTextContent
      .andThen(Serialization.read[List[Issue]])
      .andThen(_.map(Mappers.jiraIssueToWorkItem))
      .andThen(CFDReporter.generate)
      .apply(s"${dataLocation}/${projectKey}-jira-issues.json")
  }
}
