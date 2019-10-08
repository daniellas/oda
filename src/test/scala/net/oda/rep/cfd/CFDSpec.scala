package net.oda.rep.cfd

import java.time.ZonedDateTime

import net.oda.Time._
import net.oda.data.jira.JiraTimestampSerializer
import net.oda.model.WorkItemStatusHistory
import org.json4s.DefaultFormats
import org.scalatest.{FlatSpec, Matchers}

class CFDSpec extends FlatSpec with Matchers {
  implicit val formats = DefaultFormats + JiraTimestampSerializer

  it should "normalize flow" in {
    val now = ZonedDateTime.now();

    CFDReporter.normalizeFlow(
      "To Do",
      "Done",
      List(
        WorkItemStatusHistory(now.plusHours(1), "To Do"),
        WorkItemStatusHistory(now.plusHours(2), "Done"))) should contain only(
      WorkItemStatusHistory(now.plusHours(1), "To Do"),
      WorkItemStatusHistory(now.plusHours(2), "Done")
    )

    CFDReporter.normalizeFlow(
      "To Do",
      "Done",
      List(
        WorkItemStatusHistory(now.plusHours(1), "To Do"),
        WorkItemStatusHistory(now.plusHours(2), "In Progress"),
        WorkItemStatusHistory(now.plusHours(3), "Done"))) should contain only(
      WorkItemStatusHistory(now.plusHours(1), "To Do"),
      WorkItemStatusHistory(now.plusHours(3), "Done")
    )

    CFDReporter.normalizeFlow(
      "To Do",
      "Done",
      List(
        WorkItemStatusHistory(now.plusHours(1), "To Do"),
        WorkItemStatusHistory(now.plusHours(2), "In Progress"),
        WorkItemStatusHistory(now.plusHours(3), "Done"),
        WorkItemStatusHistory(now.plusHours(4), "In Progress"),
        WorkItemStatusHistory(now.plusHours(5), "Done"))) should contain only(
      WorkItemStatusHistory(now.plusHours(1), "To Do"),
      WorkItemStatusHistory(now.plusHours(5), "Done")
    )

    CFDReporter.normalizeFlow(
      "To Do",
      "Done",
      List(
        WorkItemStatusHistory(now.plusHours(1), "To Do"),
        WorkItemStatusHistory(now.plusHours(2), "Done"),
        WorkItemStatusHistory(now.plusHours(3), "To Do"),
        WorkItemStatusHistory(now.plusHours(4), "Done"))) should contain only(
      WorkItemStatusHistory(now.plusHours(1), "To Do"),
      WorkItemStatusHistory(now.plusHours(2), "Done"),
      WorkItemStatusHistory(now.plusHours(3), "To Do"),
      WorkItemStatusHistory(now.plusHours(4), "Done")
    )

    CFDReporter.normalizeFlow(
      "To Do",
      "Done",
      List(
        WorkItemStatusHistory(now.plusHours(1), "To Do"),
        WorkItemStatusHistory(now.plusHours(2), "In Progress"),
        WorkItemStatusHistory(now.plusHours(3), "In testing"),
        WorkItemStatusHistory(now.plusHours(4), "To Do"),
        WorkItemStatusHistory(now.plusHours(5), "In Progress"),
        WorkItemStatusHistory(now.plusHours(6), "In testing"),
        WorkItemStatusHistory(now.plusHours(7), "Done"))) should contain only(
      WorkItemStatusHistory(now.plusHours(1), "To Do"),
      WorkItemStatusHistory(now.plusHours(7), "Done")
    )

    CFDReporter.normalizeFlow(
      "To Do",
      "Done",
      List(
        WorkItemStatusHistory(now.plusHours(1), "To Do"),
        WorkItemStatusHistory(now.plusHours(2), "In Progress"),
        WorkItemStatusHistory(now.plusHours(3), "In testing"),
        WorkItemStatusHistory(now.plusHours(4), "In Progress"),
        WorkItemStatusHistory(now.plusHours(5), "In testing"),
        WorkItemStatusHistory(now.plusHours(6), "Done"))) should contain only(
      WorkItemStatusHistory(now.plusHours(1), "To Do"),
      WorkItemStatusHistory(now.plusHours(6), "Done")
    )

    CFDReporter.normalizeFlow(
      "To Do",
      "Done",
      List(
        WorkItemStatusHistory(now.plusHours(1), "To Do"),
        WorkItemStatusHistory(now.plusHours(2), "Invalid"),
      )) should contain only(
      WorkItemStatusHistory(now.plusHours(1), "To Do"),
      WorkItemStatusHistory(now.plusHours(2), "Done")
    )

  }

  it should "calculateCycleTime" in {
    CFDReporter.calculateCycleTime("2018-05-21", "2018-05-28") should equal(2)
    CFDReporter.calculateCycleTime("2018-05-28", "2018-08-20") should equal(13)
  }

}
