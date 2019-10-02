package net.oda.rep

import java.sql.Timestamp
import java.time.LocalDate

import net.oda.Config
import net.oda.Spark.session.implicits._
import net.oda.Time._
import net.oda.model.{WorkItem, WorkItemStatusHistory}
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions._
import org.slf4j.LoggerFactory

import scala.collection.SortedMap

case class WorkItemStatus(id: String, `type`: String, created: Timestamp, status: String, flow: String)

object CFDReporter {
  val log = LoggerFactory.getLogger("cfd")

  val referenceFlow = List("To Do", "In Progress", "In Review", "Ready to test", "In testing", "Done")
  val entryState = referenceFlow.head
  val finalState = referenceFlow.last

  val validFlows = List(
    referenceFlow,
    List("To Do"),
    List("To Do", "In Progress"),
    List("To Do", "In Progress", "In Review"),
    List("To Do", "In Progress", "In testing"),
    List("To Do", "In Progress", "Done"),
    List("To Do", "In Progress", "In Review", "Done"),
    List("To Do", "In Progress", "In testing", "Done"),
    List("To Do", "In Progress", "In Review", "Ready to test"),
    List("To Do", "In Progress", "Ready to test", "In testing", "Done"),
    List("To Do", "In Progress", "In Review", "Ready to test", "In testing")
  )

  val normalizeFlow = (history: List[WorkItemStatusHistory]) => {
    val sortedHistory = history.sortBy(_.created.getTime)
    val sortedHistoryNames = sortedHistory.map(_.name)

    validFlows
      .find(f => sortedHistoryNames.endsWith(f))
      .map(f => sortedHistory.takeRight(f.size))
      .getOrElse(List.empty)
  }

  val matchType = (item: WorkItem) => item.`type` match {
    case "Story" => true
    case "Bug" => true
    case _ => false
  }

  val flowDesc = (flow: List[String]) => flow.foldLeft("")(_ + "/" + _)

  val calculateCycleTime = (start: LocalDate, end: LocalDate) => weeksBetween(start, end) + 1

  val findDateLastBelow = (m: SortedMap[Long, Timestamp], v: Long) => {
    val to = m.to(v)

    if (to.isEmpty) {
      m.head._2
    } else {
      to.last._2
    }
  }

  val cumulativeCol = (col: String) => col + " cumulative"

  def generate(workItems: List[WorkItem]): Unit = {
    val startDate = Config.getProp("cfd.startDate").map(parseLocalDate).getOrElse(LocalDate.MIN)
    val statusHistory = workItems
      .filter(matchType)
      .map(i => WorkItem(i.id, i.name, i.`type`, i.priority, i.created, i.closed, i.createdBy, normalizeFlow(i.statusHistory)))
      .filter(_.created.after(startDate))
      .filter(_.statusHistory.nonEmpty)
      .flatMap(i => i.statusHistory.map(h => WorkItemStatus(i.id, i.`type`, weekStart(h.created), h.name, flowDesc(i.statusHistory.map(_.name)))))
      .toDF

    val counts = statusHistory
      .toDF
      .groupBy('created, 'status)
      .count
      .select(col("created").as("week"), 'status, 'count)
      .groupBy('week)
      .pivot('status)
      .sum()

    val timeRange = counts.select(min('week), max('week))
      .flatMap(r => weeksRange(r.getTimestamp(0), r.getTimestamp(1)).map(toTimestamp))
      .toDF("r_week")

    val filledCounts = timeRange
      .join(counts, 'r_week === counts("week"), "outer")
      .na.fill(0)
      .withColumn("WIP", col(entryState) - col(finalState))
      .drop('week)
      .withColumnRenamed("r_week", "week")

    val cumulativeCounts = filledCounts
      .columns
      .tail
      .foldLeft(filledCounts)(
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
      .map(r => (r.getTimestamp(0), r.getAs[Long](cumulativeCol(entryState))))
      .foldLeft(SortedMap.empty[Long, Timestamp])((acc, i) =>
        if (acc.contains(i._2)) {
          acc
        } else {
          acc + (i._2 -> i._1)
        }
      )

    val cycleTime = cumulativeCounts.select('week, col(cumulativeCol(finalState)))
      .map(r =>
        (
          r.getTimestamp(0),
          calculateCycleTime(
            findDateLastBelow(entryDatesByCount, r.getLong(1)), r.getTimestamp(0))
        )
      )
      .select('_1.as("ct_week"), '_2.as("CT"))

    cumulativeCounts
      .join(cycleTime, 'week === cycleTime("ct_week"))
      .withColumn("TH", col(cumulativeCol("WIP")) / 'CT)
      .drop('ct_week)
      .orderBy('week)
      .withColumn("week", date_format('week, "yyyy-MM-dd"))
      .repartition(1)
      .write
      .format("csv")
      .mode("overwrite")
      .option("header", value = true)
      .save(Config.getProp("reports.location").getOrElse(() => "./") + "/cfd")
  }
}
