package net.oda.rep.cfd

import java.time.LocalDateTime

import net.oda.Time.{weeksBetween, _}
import net.oda.data.jira.JiraTimestampSerializer
import net.oda.model.WorkItemStatusHistory
import org.json4s.DefaultFormats
import org.scalatest.{FlatSpec, Matchers}

class CFDSpec extends FlatSpec with Matchers {
  implicit val formats = DefaultFormats + JiraTimestampSerializer
  val stateMapping = Map("invalid" -> "done")
  val referenceFlow = Map(
    "backlog" -> 0,
    "todo" -> 1,
    "progress" -> 2,
    "review" -> 3,
    "testing" -> 4,
    "done" -> 5
  )
  val entryState = "todo"
  val finalState = "done"
  val now = LocalDateTime.of(2000, 1, 1, 0, 0, 0);

  it should "normalize flow" in {
    CFDReporter.normalizeFlow(
      referenceFlow,
      entryState,
      finalState,
      stateMapping,
      List(
        WorkItemStatusHistory(now.plusHours(1), "todo"),
        WorkItemStatusHistory(now.plusHours(2), "done"))) should contain only(
      WorkItemStatusHistory(now.plusHours(1), "todo"),
      WorkItemStatusHistory(now.plusHours(2), "done")
    )

    CFDReporter.normalizeFlow(
      referenceFlow,
      entryState,
      finalState,
      stateMapping,
      List(
        WorkItemStatusHistory(now.plusHours(1), "todo"),
        WorkItemStatusHistory(now.plusHours(2), "progress"),
        WorkItemStatusHistory(now.plusHours(3), "done"))) should contain only(
      WorkItemStatusHistory(now.plusHours(1), "todo"),
      WorkItemStatusHistory(now.plusHours(3), "done")
    )

    CFDReporter.normalizeFlow(
      referenceFlow,
      entryState,
      finalState,
      stateMapping,
      List(
        WorkItemStatusHistory(now.plusHours(1), "todo"),
        WorkItemStatusHistory(now.plusHours(2), "progress"),
        WorkItemStatusHistory(now.plusHours(3), "review"),
        WorkItemStatusHistory(now.plusHours(4), "progress"),
        WorkItemStatusHistory(now.plusHours(5), "done"))) should contain only(
      WorkItemStatusHistory(now.plusHours(1), "todo"),
      WorkItemStatusHistory(now.plusHours(5), "done")
    )

    CFDReporter.normalizeFlow(
      referenceFlow,
      entryState,
      finalState,
      stateMapping,
      List(
        WorkItemStatusHistory(now.plusHours(1), "todo"),
        WorkItemStatusHistory(now.plusHours(2), "progress"),
        WorkItemStatusHistory(now.plusHours(3), "review"),
        WorkItemStatusHistory(now.plusHours(4), "todo"),
        WorkItemStatusHistory(now.plusHours(5), "progress"),
        WorkItemStatusHistory(now.plusHours(6), "testing"),
        WorkItemStatusHistory(now.plusHours(7), "done"))) should contain only(
      WorkItemStatusHistory(now.plusHours(1), "todo"),
      WorkItemStatusHistory(now.plusHours(7), "done")
    )

    CFDReporter.normalizeFlow(
      referenceFlow,
      entryState,
      finalState,
      stateMapping,
      List(
        WorkItemStatusHistory(now.plusHours(1), "todo"),
        WorkItemStatusHistory(now.plusHours(2), "progress"),
        WorkItemStatusHistory(now.plusHours(3), "testing"),
        WorkItemStatusHistory(now.plusHours(4), "progress"),
        WorkItemStatusHistory(now.plusHours(5), "testing"),
        WorkItemStatusHistory(now.plusHours(6), "done"))) should contain only(
      WorkItemStatusHistory(now.plusHours(1), "todo"),
      WorkItemStatusHistory(now.plusHours(6), "done")
    )

    CFDReporter.normalizeFlow(
      referenceFlow,
      entryState,
      finalState,
      stateMapping,
      List(
        WorkItemStatusHistory(now.plusHours(1), "todo"),
        WorkItemStatusHistory(now.plusHours(2), "invalid"),
      )) should contain only(
      WorkItemStatusHistory(now.plusHours(1), "todo"),
      WorkItemStatusHistory(now.plusHours(2), "done")
    )

  }

  it should "ignore undefined following states" in {
    CFDReporter.normalizeFlow(
      referenceFlow,
      entryState,
      finalState,
      stateMapping,
      List(
        WorkItemStatusHistory(now.plusHours(1), "backlog"),
        WorkItemStatusHistory(now.plusHours(2), "todo"),
        WorkItemStatusHistory(now.plusHours(3), "progress"),
        WorkItemStatusHistory(now.plusHours(4), "backlog")
      )) should be(Nil)
  }

  it should "ignore undefined states" in {
    CFDReporter.normalizeFlow(
      referenceFlow,
      entryState,
      finalState,
      stateMapping,
      List(
        WorkItemStatusHistory(now.plusHours(1), "backlog"),
        WorkItemStatusHistory(now.plusHours(2), "todo"),
        WorkItemStatusHistory(now.plusHours(3), "done")
      )) should contain only(
      WorkItemStatusHistory(now.plusHours(2), "todo"),
      WorkItemStatusHistory(now.plusHours(3), "done")
    )
  }

  it should "use first entry state" in {
    CFDReporter.normalizeFlow(
      referenceFlow,
      entryState,
      finalState,
      stateMapping,
      List(
        WorkItemStatusHistory(now.plusHours(1), "todo"),
        WorkItemStatusHistory(now.plusHours(2), "done"),
        WorkItemStatusHistory(now.plusHours(3), "todo"),
        WorkItemStatusHistory(now.plusHours(4), "done"))) should contain only(
      WorkItemStatusHistory(now.plusHours(1), "todo"),
      WorkItemStatusHistory(now.plusHours(4), "done")
    )
  }

  it should "calculateCycleTime" in {
    CFDReporter.calculateCycleTime(weeksBetween, "2018-05-21", "2018-05-28") should equal(2)
    CFDReporter.calculateCycleTime(weeksBetween, "2018-05-28", "2018-08-20") should equal(13)
  }

}
