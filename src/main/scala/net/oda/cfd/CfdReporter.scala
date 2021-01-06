package net.oda.cfd

import java.sql.Timestamp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

import com.typesafe.scalalogging.Logger
import net.oda.Spark.session.implicits._
import net.oda.Time
import net.oda.Time._
import net.oda.workitem.{Status, WorkItem}
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.DoubleType
import org.apache.spark.sql.{Column, Dataset, DatasetHolder, Row}

import scala.collection.SortedMap

case class CfdItem(
                    id: String,
                    `type`: String,
                    created: Timestamp,
                    status: String,
                    estimate: Double)

object CfdReporter {
  private val log = Logger("cfd-reporter")

  val calculateCycleTime = (tsDiffCalculator: (LocalDate, LocalDate) => Long, start: LocalDate, end: LocalDate) => tsDiffCalculator(start, end) + 1

  val findDateLastBelow = (m: SortedMap[Double, (Timestamp, Double)], v: Double) => {
    val to = m.to(v)

    if (to.isEmpty) {
      None
    } else {
      Option(to.last._2._1)
    }
  }

  val findFinalStateLastBelow = (m: SortedMap[Double, (Timestamp, Double)], v: Double) => {
    val to = m.to(v)

    if (to.isEmpty) {
      None
    } else {
      Option(to.last._2._2)
    }
  }

  val cumulativeCol = (col: String) => col + " cumulative"
  val changeCol = (col: String) => col + " change"

  val currentWipCol = "WIP current"
  val wipCol = "WIP"
  val createdCol = "created"
  val thCol = "TH"
  val timeCol = "Time"
  val ctCol = "CT"
  val doneLaggedByCTCol = "doneLaggedByCT"

  val countAggregate = count(lit(1));
  val sumEstimateAggregate = sum('estimate)

  val normalizeFlow = (
                        referenceFlow: Map[String, Int],
                        entryState: String,
                        finalState: String,
                        stateMapping: Map[String, String],
                        history: Seq[Status]) =>
    history
      .filter(s => referenceFlow.contains(stateMapping.get(s.name).getOrElse(s.name)))
      .sortBy(_.created.getTime)
      .map(i => Status(i.created, stateMapping.get(i.name).getOrElse(i.name), None))
      .foldLeft(List.empty[Status])(
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

  def normalizeWorkItems(
                          workItems: Seq[WorkItem],
                          referenceFlow: Map[String, Int],
                          entryState: String,
                          finalState: String,
                          stateMapping: Map[String, String]): Seq[WorkItem] = {
    workItems
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
        normalizeFlow(referenceFlow, entryState, finalState, stateMapping, i.statusHistory),
        i.epicName,
        i.version))
      .filter(_.statusHistory.nonEmpty)
  }

  def generate(
                projectKey: String,
                startDate: LocalDate,
                itemType: String => Boolean,
                priority: String => Boolean,
                referenceFlow: Map[String, Int],
                entryState: String,
                finalState: String,
                stateMapping: Map[String, String],
                interval: ChronoUnit,
                aggregate: Column,
                workItems: Seq[WorkItem]): Dataset[Row] = {
    log.info("{} CFD report generation started", projectKey)

    val createTsMapper = Time.interval.apply(interval, _)
    val tsDiffCalculator = (start: LocalDate, end: LocalDate) => interval.between(start, end)
    val rangeProvider = (start: LocalDate, end: LocalDate) => (0L to tsDiffCalculator.apply(start, end)).toList.map(start.plus(_, interval))

    val statusHistory = normalizeWorkItems(
      workItems
        .filter(i => itemType.apply(i.`type`))
        .filter(i => priority.apply(i.priority))
        .filter(i => startDate == LocalDate.MIN || i.created.after(startDate)),
      referenceFlow,
      entryState,
      finalState,
      stateMapping)
      .flatMap(i => i.statusHistory.map(h => CfdItem(i.id, i.`type`, createTsMapper(h.created), h.name, i.estimate)))
      .toDF

    //    statusHistory.groupBy('id).count.filter('count === 1).show

    if (statusHistory.isEmpty) {
      return statusHistory
    }

    val values = statusHistory
      .groupBy('created, 'status)
      .agg(aggregate.alias("val"))
      .select(col(createdCol), 'status, 'val)
      .groupBy('created)
      .pivot('status)
      .sum()

    val timeRange = values.select(min('created), max('created))
      .flatMap(r => rangeProvider(r.getTimestamp(0), r.getTimestamp(1)).map(toTimestamp))
      .toDF("r_" + createdCol)

    val filledValues = timeRange
      .join(values, 'r_created === values(createdCol), "outer")
      .na.fill(0)
      .withColumn(currentWipCol, col(entryState) - col(finalState))
      .drop('created)
      .withColumnRenamed("r_" + createdCol, createdCol)

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

    val entryValuesByEntryState = cumulativeValues
      .select(
        'created,
        col(cumulativeCol(entryState)).cast(DoubleType),
        col(cumulativeCol(finalState)).cast(DoubleType)
      )
      .collect
      .map(r => (
        r.getAs[Double](cumulativeCol(entryState)),
        r.getTimestamp(0),
        r.getAs[Double](cumulativeCol(finalState))))
      .foldLeft(SortedMap.empty[Double, (Timestamp, Double)])((acc, i) => acc + (i._1 -> (i._2, i._3)))

    val cycleTime = cumulativeValues.select(
      'created,
      col(cumulativeCol(entryState)).cast(DoubleType),
      col(cumulativeCol(finalState)).cast(DoubleType))
      .map(r =>
        (
          r.getTimestamp(0),
          if (r.getDouble(1) == r.getDouble(2)) 1L
          else findDateLastBelow(entryValuesByEntryState, r.getDouble(2))
            .map(calculateCycleTime(tsDiffCalculator, _, r.getTimestamp(0)))
            .getOrElse(0L),
          findFinalStateLastBelow(entryValuesByEntryState, r.getDouble(2))
            .getOrElse(0D)
        )
      )
      .select(
        '_1.as("ct_" + createdCol),
        '_2.as(ctCol),
        '_3.as(doneLaggedByCTCol))

    val wip = udf((ct: Long, doneChange: Double, done: Double, doneLagged: Double) => {
      if (ct == 1) doneChange else done - doneLagged
    })

    val res = cumulativeValues
      .join(cycleTime, 'created === cycleTime("ct_" + createdCol))
      .orderBy('created)
      .drop("ct_" + createdCol, currentWipCol)
      .withColumnRenamed(entryState, changeCol(entryState))
      .withColumnRenamed(finalState, changeCol(finalState))
      .withColumnRenamed("created", timeCol)
      .withColumnRenamed(cumulativeCol(entryState), entryState)
      .withColumnRenamed(cumulativeCol(finalState), finalState)
      .withColumnRenamed(cumulativeCol(currentWipCol), currentWipCol)
      .withColumn(wipCol, wip(
        col(ctCol),
        col(changeCol(finalState)),
        col(finalState),
        col(doneLaggedByCTCol)))
      .withColumn(thCol, col(wipCol) / col(ctCol))

    log.info("{} CFD report generation complete", projectKey)
    res
  }

  def calculateWorkItemsDuration(
                                  projectKey: String,
                                  startDate: LocalDate,
                                  itemType: String => Boolean,
                                  priority: String => Boolean,
                                  referenceFlow: Map[String, Int],
                                  entryState: String,
                                  finalState: String,
                                  stateMapping: Map[String, String],
                                  interval: ChronoUnit,
                                  workItems: Seq[WorkItem]): Dataset[Row] = {
    val createdMapper = udf(Time.interval.apply(interval, _))
    val duration = udf(Time.daysBetweenTimestamps(_, _))

    normalizeWorkItems(
      workItems
        .filter(i => itemType.apply(i.`type`))
        .filter(i => priority.apply(i.priority))
        .filter(i => startDate == LocalDate.MIN || i.created.after(startDate)),
      referenceFlow,
      entryState,
      finalState,
      stateMapping)
      .flatMap(i => i.statusHistory.map(h => CfdItem(i.id, i.`type`, h.created, h.name, i.estimate)))
      .toDF
      .select('id, 'status, 'created)
      .groupBy('id)
      .agg(min('created).as('start), max('created).as('end))
      .withColumn("durationDays", duration('start, 'end) + 1)
      .withColumn("created", createdMapper('start))
      .groupBy('created)
      .agg(
        avg('durationDays).as("mean"),
        min('durationDays).as("min"),
        max('durationDays).as("max"))
  }

  def calculateAggregates(data: Dataset[Row]) = {
    data
      .agg(
        Map(
          thCol -> "avg",
          ctCol -> "avg",
          currentWipCol -> "avg",
        ))
  }
}
