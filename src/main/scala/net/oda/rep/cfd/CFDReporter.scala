package net.oda.rep.cfd

import java.sql.Timestamp
import java.time.temporal.ChronoUnit
import java.time.{LocalDate, ZonedDateTime}

import com.typesafe.scalalogging.Logger
import net.oda.Spark.session.implicits._
import net.oda.Time._
import net.oda.model.{WorkItem, WorkItemStatusHistory}
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions._
import org.apache.spark.sql.{Column, Dataset, Row}

import scala.collection.SortedMap

case class Item(id: String, `type`: String, created: Timestamp, status: String, estimate: Int)

object CFDReporter {
  private val log = Logger("cfd-reporter")

  val normalizeFlow = (
                        referenceFlow: SortedMap[String, Int],
                        entryState: String,
                        finalState: String,
                        stateMapping: Map[String, String],
                        history: Seq[WorkItemStatusHistory]) => {
    val sortedHistory = history
      .sortBy(_.created.getTime)
      .map(i => WorkItemStatusHistory(
        i.created,
        stateMapping.get(i.name).getOrElse(i.name)
      ))

    sortedHistory
      .foldLeft(List.empty[WorkItemStatusHistory])(
        (acc, i) => {
          if (acc.isEmpty && i.name == entryState) {
            i :: acc
          } else if (acc.headOption.map(_.name).contains(entryState) && i.name == finalState) {
            i :: acc
          } else if (acc.headOption.map(_.name).contains(finalState) && i.name == entryState) {
            acc.tail
          } else if (acc.headOption.map(_.name).contains(entryState) && i.name != finalState) {
            if (referenceFlow(i.name) < referenceFlow(acc.head.name)) {
              Nil
            } else {
              acc
            }
          } else if (acc.headOption.map(_.name).contains(finalState) && i.name != entryState) {
            Nil
          } else {
            acc
          }
        }
      )
      .sortBy(_.created.getTime)
  }

  val calculateCycleTime = (tsDiffCalculator: (LocalDate, LocalDate) => Long, start: LocalDate, end: LocalDate) => tsDiffCalculator(start, end) + 1

  val findDateLastBelow = (m: SortedMap[Long, Timestamp], v: Long) => {
    val to = m.to(v)

    if (to.isEmpty) {
      None
    } else {
      Option(to.last._2)
    }
  }

  val cumulativeCol = (col: String) => col + " cumulative"

  def generate(
                project: String,
                startDate: LocalDate,
                itemType: String => Boolean,
                priority: String => Boolean,
                referenceFlow: SortedMap[String, Int],
                entryState: String,
                finalState: String,
                stateMapping: Map[String, String],
                interval: ChronoUnit,
                aggregate: Column,
                workItems: List[WorkItem]): Dataset[Row] = {
    log.info("CFD report generation started")
    val createTsMapper = (ts: ZonedDateTime) => if (interval == ChronoUnit.DAYS) day(ts) else weekStart(ts)
    val tsDiffCalculator = (start: LocalDate, end: LocalDate) => interval.between(start, end)
    val rangeProvider = (start: LocalDate, end: LocalDate) => (0L to tsDiffCalculator.apply(start, end)).toList.map(start.plus(_, interval))

    val statusHistory = workItems
      .filter(i => itemType.apply(i.`type`))
      .filter(i => priority.apply(i.priority))
      .filter(i => startDate == LocalDate.MIN || i.created.after(startDate))
      .map(i => WorkItem(
        i.id,
        i.name,
        i.`type`,
        i.priority,
        i.created,
        i.closed,
        i.createdBy,
        i.size,
        i.estimate,
        normalizeFlow(referenceFlow, entryState, finalState, stateMapping, i.statusHistory)))
      .filter(_.statusHistory.nonEmpty)
      .flatMap(i => i.statusHistory.map(h => Item(i.id, i.`type`, createTsMapper(h.created), h.name, i.estimate)))
      .toDF

    val values = statusHistory
      .groupBy('created, 'status)
      .agg(aggregate.alias("val"))
      .select(col("created"), 'status, 'val)
      .groupBy('created)
      .pivot('status)
      .sum()

    val timeRange = values.select(min('created), max('created))
      .flatMap(r => rangeProvider(r.getTimestamp(0), r.getTimestamp(1)).map(toTimestamp))
      .toDF("r_created")

    val filledValues = timeRange
      .join(values, 'r_created === values("created"), "outer")
      .na.fill(0)
      .withColumn("WIP", col(entryState) - col(finalState))
      .drop('created)
      .withColumnRenamed("r_created", "created")

    val cumulativeValues = filledValues
      .columns
      .tail
      .foldLeft(filledValues)(
        (acc, i) => acc.withColumn(
          cumulativeCol(i),
          sum(i)
            .over(
              Window
                .orderBy('created)
                .rowsBetween(Window.unboundedPreceding, Window.currentRow)))
      )

    val entryDatesByValue = cumulativeValues.select('created, col(cumulativeCol(entryState)))
      .collect
      .map(r => (r.getTimestamp(0), r.getAs[Long](cumulativeCol(entryState))))
      .foldLeft(SortedMap.empty[Long, Timestamp])((acc, i) => acc + (i._2 -> i._1))

    val cycleTime = cumulativeValues.select('created, col(cumulativeCol(entryState)), col(cumulativeCol(finalState)))
      .map(r =>
        (
          r.getTimestamp(0),
          if (r.getLong(1) == r.getLong(2)) 0
          else findDateLastBelow(entryDatesByValue, r.getLong(2))
            .map(calculateCycleTime(tsDiffCalculator, _, r.getTimestamp(0)))
            .getOrElse(1L)
        )
      )
      .select('_1.as("ct_created"), '_2.as("CT"))

    val res = cumulativeValues
      .join(cycleTime, 'created === cycleTime("ct_created"))
      .withColumn("TH", col(cumulativeCol("WIP")) / 'CT)
      .drop('ct_created)
      .orderBy('created)
      .withColumn("created", date_format('created, "yyyy-MM-dd"))
      .repartition(1)

    log.info("CFD report generation complete")
    res
  }
}
