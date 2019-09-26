package net.oda.rep

import java.sql.Timestamp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

import net.oda.Mappers._
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

  val getLongOption = (row: Row, idx: Int) => if (row.isNullAt(idx)) None else Some(row.getLong(idx))

  case class CfdItem(created: String, counts: Map[String, Long])

  val currentOrPrevLongOption: (Row, Int, Option[CfdItem], String) => Option[Long] = (row: Row, idx: Int, prev: Option[CfdItem], key: String) =>
    getLongOption(row, idx)
      .orElse(prev.map(_.counts(key)))

  val calculateStatusCounts = (cols: Array[String], acc: List[CfdItem], row: Row) =>
    (1 to cols.length - 1)
      .foldLeft(Map.empty[String, Long])((a, i) => a + (cols(i) -> currentOrPrevLongOption(row, i, acc.headOption, cols(i)).getOrElse(0L)))

  val calculateWip = (counts: Map[String, Long]) => counts + ("WIP" -> (counts(entryState) - counts(finalState)))

  val applyCalculations = (cols: Array[String], acc: List[CfdItem], row: Row) => {
    CfdItem(
      row.getString(0),
      calculateWip(calculateStatusCounts(cols, acc, row))
    ) :: acc
  }

  val calculateCycleTime = (start: String, end: String) => ChronoUnit.WEEKS.between(LocalDate.parse(end), LocalDate.parse(start)) + 1

  val findDateLastBelow = (m: SortedMap[Long, Array[(String, Long)]], v: Long) => m.to(v).last._2.head._1

  def generate(workItems: List[WorkItem]) = {
    val validWorkItems = Spark.ctx.parallelize(workItems)
      .filter(matchType)
      .map(i => WorkItem(i.id, i.name, i.`type`, i.priority, i.created, i.closed, i.createdBy, normalizeFlow(i.statusHistory)))
      .filter(!_.statusHistory.isEmpty)

    val statusCountsByPeriod = validWorkItems
      .flatMap(i => i.statusHistory.map(h => WorkItemStatus(i.id, i.`type`, weekStart(h.created), h.name, flowDesc(i.statusHistory.map(_.name)))))
      .toDF
      .groupBy('created, 'status)
      .count
      .withColumn(
        "cummulativeCount",
        sum('count)
          .over(
            Window
              .partitionBy('status)
              .orderBy('created)
              .rowsBetween(Window.unboundedPreceding, Window.currentRow)))
      .select(date_format('created, "yyyy-MM-dd").as("created"), 'status, 'cummulativeCount)
      .groupBy('created)
      .pivot('status)
      .sum()
      .orderBy('created)

    val statusCountsWip = statusCountsByPeriod.collect()
      .foldLeft(List[CfdItem]())(applyCalculations(statusCountsByPeriod.columns, _, _))

    val pivot = Spark.ctx.parallelize(statusCountsWip)
      .toDF()
      .select('created, explode('counts))
      .groupBy('created)
      .pivot('key)
      .sum()
      .orderBy('created)

    val entryDatesByCount = SortedMap(
      pivot.select('created, col(entryState))
        .collect
        .map(r => (r.getString(0), r.getAs[Long](entryState)))
        .groupBy(_._2)
        .toSeq: _*)

    val cycleTime = pivot.select('created, col(finalState))
      .orderBy('created)
      .map(r => (
        r.getString(0),
        calculateCycleTime(
          r.getString(0),
          findDateLastBelow(entryDatesByCount, r.getLong(1)))))
      .select('_1.as("ct_week"), '_2.as("CT"))

    pivot
      .join(cycleTime, 'created === cycleTime("ct_week"))
      .orderBy('created)
      .withColumnRenamed("created", "week")
      .repartition(1)
      .write
      .format("csv")
      .mode("overwrite")
      .option("header", true)
      .save(Config.getProp("reports.location").getOrElse(() => "./") + "/cfd")
  }
}
