package net.oda.rep

import java.time.ZonedDateTime

import net.oda.Time._
import net.oda.data.jira.JiraTimestampSerializer
import net.oda.model.WorkItemStatusHistory
import org.json4s.DefaultFormats
import org.scalatest.{FlatSpec, Matchers}

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

  it should "calculateCycleTime" in {
    CFDReporter.calculateCycleTime("2018-05-21", "2018-05-28") should equal(2)
    CFDReporter.calculateCycleTime("2018-05-28", "2018-08-20") should equal(13)
  }

}
