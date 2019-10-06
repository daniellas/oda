package net.oda.cfd

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

    val res = CFDReporter.normalizeFlow(
      List(
        WorkItemStatusHistory(now.plusHours(0), "Backlog"),
        WorkItemStatusHistory(now.plusHours(1), "Upcoming"),
        WorkItemStatusHistory(now.plusHours(2), "To Do"),
        WorkItemStatusHistory(now.plusHours(3), "In Progress"),
        WorkItemStatusHistory(now.plusHours(4), "In Review"),
        WorkItemStatusHistory(now.plusHours(5), "Ready to test"),
        WorkItemStatusHistory(now.plusHours(6), "In testing"),
        WorkItemStatusHistory(now.plusHours(7), "Done")))

    res should contain only
      (
        WorkItemStatusHistory(now.plusHours(2), "To Do"),
        WorkItemStatusHistory(now.plusHours(3), "In Progress"),
        WorkItemStatusHistory(now.plusHours(4), "In Review"),
        WorkItemStatusHistory(now.plusHours(5), "Ready to test"),
        WorkItemStatusHistory(now.plusHours(6), "In testing"),
        WorkItemStatusHistory(now.plusHours(7), "Done"))
  }

  it should "calculateCycleTime" in {
    CFDReporter.calculateCycleTime("2018-05-21", "2018-05-28") should equal(2)
    CFDReporter.calculateCycleTime("2018-05-28", "2018-08-20") should equal(13)
  }

}
