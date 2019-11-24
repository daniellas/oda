package net.oda.cfd

import java.time.LocalDateTime

import net.oda.Time.{weeksBetween, _}
import net.oda.jira.JiraTimestampSerializer
import net.oda.workitem.Status
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
    CfdReporter.normalizeFlow(
      referenceFlow,
      entryState,
      finalState,
      stateMapping,
      List(
        Status(now.plusHours(1), "todo"),
        Status(now.plusHours(2), "done"))) should contain only(
      Status(now.plusHours(1), "todo"),
      Status(now.plusHours(2), "done")
    )

    CfdReporter.normalizeFlow(
      referenceFlow,
      entryState,
      finalState,
      stateMapping,
      List(
        Status(now.plusHours(1), "todo"),
        Status(now.plusHours(2), "progress"),
        Status(now.plusHours(3), "done"))) should contain only(
      Status(now.plusHours(1), "todo"),
      Status(now.plusHours(3), "done")
    )

    CfdReporter.normalizeFlow(
      referenceFlow,
      entryState,
      finalState,
      stateMapping,
      List(
        Status(now.plusHours(1), "todo"),
        Status(now.plusHours(2), "progress"),
        Status(now.plusHours(3), "review"),
        Status(now.plusHours(4), "progress"),
        Status(now.plusHours(5), "done"))) should contain only(
      Status(now.plusHours(1), "todo"),
      Status(now.plusHours(5), "done")
    )

    CfdReporter.normalizeFlow(
      referenceFlow,
      entryState,
      finalState,
      stateMapping,
      List(
        Status(now.plusHours(1), "todo"),
        Status(now.plusHours(2), "progress"),
        Status(now.plusHours(3), "review"),
        Status(now.plusHours(4), "todo"),
        Status(now.plusHours(5), "progress"),
        Status(now.plusHours(6), "testing"),
        Status(now.plusHours(7), "done"))) should contain only(
      Status(now.plusHours(1), "todo"),
      Status(now.plusHours(7), "done")
    )

    CfdReporter.normalizeFlow(
      referenceFlow,
      entryState,
      finalState,
      stateMapping,
      List(
        Status(now.plusHours(1), "todo"),
        Status(now.plusHours(2), "progress"),
        Status(now.plusHours(3), "testing"),
        Status(now.plusHours(4), "progress"),
        Status(now.plusHours(5), "testing"),
        Status(now.plusHours(6), "done"))) should contain only(
      Status(now.plusHours(1), "todo"),
      Status(now.plusHours(6), "done")
    )

    CfdReporter.normalizeFlow(
      referenceFlow,
      entryState,
      finalState,
      stateMapping,
      List(
        Status(now.plusHours(1), "todo"),
        Status(now.plusHours(2), "invalid"),
      )) should contain only(
      Status(now.plusHours(1), "todo"),
      Status(now.plusHours(2), "done")
    )

  }

  it should "ignore undefined following states" in {
    CfdReporter.normalizeFlow(
      referenceFlow,
      entryState,
      finalState,
      stateMapping,
      List(
        Status(now.plusHours(1), "backlog"),
        Status(now.plusHours(2), "todo"),
        Status(now.plusHours(3), "progress"),
        Status(now.plusHours(4), "backlog")
      )) should be(Nil)
  }

  it should "ignore undefined states" in {
    CfdReporter.normalizeFlow(
      referenceFlow,
      entryState,
      finalState,
      stateMapping,
      List(
        Status(now.plusHours(1), "backlog"),
        Status(now.plusHours(2), "todo"),
        Status(now.plusHours(3), "done")
      )) should contain only(
      Status(now.plusHours(2), "todo"),
      Status(now.plusHours(3), "done")
    )
  }

  it should "use first entry state" in {
    CfdReporter.normalizeFlow(
      referenceFlow,
      entryState,
      finalState,
      stateMapping,
      List(
        Status(now.plusHours(1), "todo"),
        Status(now.plusHours(2), "done"),
        Status(now.plusHours(3), "todo"),
        Status(now.plusHours(4), "done"))) should contain only(
      Status(now.plusHours(1), "todo"),
      Status(now.plusHours(4), "done")
    )
  }

  it should "calculateCycleTime" in {
    CfdReporter.calculateCycleTime(weeksBetween, "2018-05-21", "2018-05-28") should equal(2)
    CfdReporter.calculateCycleTime(weeksBetween, "2018-05-28", "2018-08-20") should equal(13)
  }

}
