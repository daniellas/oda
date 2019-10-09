package net.oda.rep.cfd

import java.sql.Timestamp
import java.time.{LocalDate, ZonedDateTime}

import net.oda.Config
import net.oda.Spark.session.implicits._
import net.oda.Time._
import net.oda.json.LocalDateSerializer
import net.oda.model.{WorkItem, WorkItemStatusHistory}
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions._
import org.apache.spark.sql.{Dataset, Row}
import org.json4s.DefaultFormats

import scala.collection.SortedMap

case class Item(id: String, `type`: String, created: Timestamp, status: String)

case class Report(
                   project: String,
                   startDate: LocalDate,
                   metrics: Seq[Map[String, Any]])

object CFDReporter {
  implicit val formats = DefaultFormats + LocalDateSerializer


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
                itemType: String => Boolean,
                priority: String => Boolean,
                referenceFLow: SortedMap[String, Int],
                entryState: String,
                finalState: String,
                stateMapping: Map[String, String],
                createTsMapper: ZonedDateTime => ZonedDateTime,
                tsDiffCalculator: (LocalDate, LocalDate) => Long,
                rangeProvider: (LocalDate, LocalDate) => List[LocalDate],
                workItems: List[WorkItem]): Dataset[Row] = {
    val startDate = Config.getProp("cfd.startDate").map(parseLocalDate).getOrElse(LocalDate.MIN)

    val statusHistory = workItems
      .filter(i => itemType.apply(i.`type`))
      .filter(i => priority.apply(i.priority))
      .map(i => WorkItem(
        i.id,
        i.name,
        i.`type`,
        i.priority,
        i.created,
        i.closed,
        i.createdBy,
        normalizeFlow(referenceFLow, entryState, finalState, stateMapping, i.statusHistory)))
      .filter(_.created.after(startDate))
      .filter(_.statusHistory.nonEmpty)
      .flatMap(i => i.statusHistory.map(h => Item(i.id, i.`type`, createTsMapper(h.created), h.name)))
      .toDF

    val counts = statusHistory
      .toDF
      .groupBy('created, 'status)
      .count
      .select(col("created"), 'status, 'count)
      .groupBy('created)
      .pivot('status)
      .sum()

    val timeRange = counts.select(min('created), max('created))
      .flatMap(r => rangeProvider(r.getTimestamp(0), r.getTimestamp(1)).map(toTimestamp))
      .toDF("r_created")

    val filledCounts = timeRange
      .join(counts, 'r_created === counts("created"), "outer")
      .na.fill(0)
      .withColumn("WIP", col(entryState) - col(finalState))
      .drop('created)
      .withColumnRenamed("r_created", "created")

    val cumulativeCounts = filledCounts
      .columns
      .tail
      .foldLeft(filledCounts)(
        (acc, i) => acc.withColumn(
          cumulativeCol(i),
          sum(i)
            .over(
              Window
                .orderBy('created)
                .rowsBetween(Window.unboundedPreceding, Window.currentRow)))
      )

    val entryDatesByCount = cumulativeCounts.select('created, col(cumulativeCol(entryState)))
      .collect
      .map(r => (r.getTimestamp(0), r.getAs[Long](cumulativeCol(entryState))))
      .foldLeft(SortedMap.empty[Long, Timestamp])((acc, i) => acc + (i._2 -> i._1))

    val cycleTime = cumulativeCounts.select('created, col(cumulativeCol(entryState)), col(cumulativeCol(finalState)))
      .map(r =>
        (
          r.getTimestamp(0),
          if (r.getLong(1) == r.getLong(2)) 0
          else findDateLastBelow(entryDatesByCount, r.getLong(2))
            .map(calculateCycleTime(tsDiffCalculator, _, r.getTimestamp(0)))
            .getOrElse(1L)
        )
      )
      .select('_1.as("ct_created"), '_2.as("CT"))

    cumulativeCounts
      .join(cycleTime, 'created === cycleTime("ct_created"))
      .withColumn("TH", col(cumulativeCol("WIP")) / 'CT)
      .drop('ct_created)
      .orderBy('created)
      .withColumn("created", date_format('created, "yyyy-MM-dd"))
      .repartition(1)


    //    JsonSer.writeToFile(
    //      formats,
    //      Config.getProp("reports.location").getOrElse(() => "./") + s"/cfd-${project}.json",
    //      Report(
    //        project,
    //        startDate,
    //        report
    //          .collect
    //          .map(r => report.columns.foldLeft(Map.empty[String, Any])((acc, i) => acc + (i -> r.getAs[Any](i)))))
    //    )

    //    report
    //      .write
    //      .format("csv")
    //      .mode("overwrite")
    //      .option("header", value = true)
    //      .save(Config.getProp("reports.location").getOrElse(() => "./") + s"/cfd-${project}")
  }
}
