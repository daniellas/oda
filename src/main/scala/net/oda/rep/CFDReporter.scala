package net.oda.rep

import java.sql.Timestamp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

import net.oda.Mappers._
import net.oda.Time._
import net.oda.Spark.session.implicits._
import net.oda.model.{WorkItem, WorkItemStatusHistory}
import net.oda.{Config, Spark}
import org.apache.spark.sql.Row
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions._

import scala.collection.SortedMap

case class WorkItemStatus(id: String, `type`: String, created: Timestamp, status: String, flow: String)

object CFDReporter {

  val referenceFlow = List("To Do", "In Progress", "In Review", "Ready to test", "In testing", "Done")
  val entryState = referenceFlow.head
  val finalState = referenceFlow.last

  val validFlows = List(
    referenceFlow,
    List("To Do", "In Progress", "In Review", "Done"),
    List("To Do", "In Progress", "Ready to test", "In testing", "Done"),
    List("To Do", "In Progress", "In testing", "Done"),
    List("To Do", "In Progress", "Done"),
    List("To Do", "Done"))

  val normalizeFlow = (history: List[WorkItemStatusHistory]) => {
    validFlows
      .find(f => history.sortBy(_.created.getTime).map(_.name).endsWith(f))
      .map(f => history.takeRight(f.size).sortBy(_.created.getTime))
      .getOrElse(List.empty)
  }

  val matchType = (item: WorkItem) => item.`type` match {
    case "Story" => true
    case "Bug" => true
    case _ => false
  }

  val flowDesc = (flow: List[String]) => flow.foldLeft("")(_ + "/" + _)

  val calculateCycleTime = (start: LocalDate, end: LocalDate) => weeksBetween(start, end) + 1

  val findDateLastBelow = (m: SortedMap[Long, String], v: Long) => m.to(v).last._2

  val cumulativeCol = (col: String) => col + " cumulative"

  def generate(workItems: List[WorkItem]) = {
    val statusHistory = workItems
      .filter(matchType)
      .map(i => WorkItem(i.id, i.name, i.`type`, i.priority, i.created, i.closed, i.createdBy, normalizeFlow(i.statusHistory)))
      .filter(_.statusHistory.nonEmpty)
      .flatMap(i => i.statusHistory.map(h => WorkItemStatus(i.id, i.`type`, weekStart(h.created), h.name, flowDesc(i.statusHistory.map(_.name)))))

    val counts = statusHistory
      .toDF
      .groupBy('created, 'status)
      .count
      .select(date_format('created, "yyyy-MM-dd").as("week"), 'status, 'count)
      .groupBy('week)
      .pivot('status)
      .sum()
      .na.fill(0)
      .withColumn("WIP", col(entryState) - col(finalState))

    val cumulativeCounts = counts
      .columns
      .tail
      .foldLeft(counts)(
        (acc, i) => acc.withColumn(
          cumulativeCol(i),
          sum(i)
            .over(
              Window
                .orderBy('week)
                .rowsBetween(Window.unboundedPreceding, Window.currentRow)))
      )


    val entryDatesByCount = cumulativeCounts.select('week, col(cumulativeCol(entryState)))
      .collect
      .map(r => (r.getString(0), r.getAs[Long](cumulativeCol(entryState))))
      .foldLeft(SortedMap.empty[Long, String])((acc, i) =>
        acc.contains(i._2) match {
          case true => acc
          case false => acc + (i._2 -> i._1)
        }
      )

    val cycleTime = cumulativeCounts.select('week, col(cumulativeCol(finalState)))
      .map(r =>
        (
          r.getString(0),
          calculateCycleTime(findDateLastBelow(entryDatesByCount, r.getLong(1)), r.getString(0))
        )
      )
      .select('_1.as("ct_week"), '_2.as("CT"))

    cumulativeCounts
      .join(cycleTime, 'week === cycleTime("ct_week"))
      .withColumn("TH", col(cumulativeCol("WIP")) / 'CT)
      .drop('ct_week)
      .orderBy('week)
      .repartition(1)
      .write
      .format("csv")
      .mode("overwrite")
      .option("header", true)
      .save(Config.getProp("reports.location").getOrElse(() => "./") + "/cfd")
  }
}
