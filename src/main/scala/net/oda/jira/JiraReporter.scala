package net.oda.jira

import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit

import net.oda.Spark.session.implicits._
import net.oda.{Spark, Time}
import net.oda.workitem.{WorkItem, WorkItems}
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions._

object JiraReporter {

  case class TypePriority(ts: Timestamp, `type`: String, priority: String)

  def workItemsChangeLog(
                          workItems: Seq[WorkItem],
                          interval: ChronoUnit) = {
    WorkItems.flatten(workItems)
      .map(i => i.mapTimes(i, Time.interval(interval, _)))
  }

  def countByTypePriority(
                           workItems: Seq[WorkItem],
                           stateMapping: Map[String, String],
                           interval: ChronoUnit) = {
    workItems
      .map(i => TypePriority(Time.interval(interval, i.created), i.`type`, i.priority))
      .toDF
      .groupBy('type, 'priority, 'ts)
      .count
  }

  case class StatusAuthor(ts: Timestamp, status: String, author: Option[String])

  def countDistinctAuthor(
                           workItems: Seq[WorkItem],
                           stateFilter: String => Boolean,
                           interval: ChronoUnit) = {
    WorkItems.flatten(workItems)
      .filter(i => stateFilter.apply(i.statusName))
      .map(i => StatusAuthor(Time.interval(interval, i.statusCreated), i.statusName, i.statusAuthor))
      .toDF
      .groupBy('ts)
      .agg(countDistinct('author))
  }

  def teamProductivityFactor(
                              workItems: Seq[WorkItem],
                              stateFilter: String => Boolean,
                              interval: ChronoUnit,
                              learningTime: Double) = {
    val range = udf(Time.range(interval, _, _))
    val experience = udf((e: Long) => if (e < learningTime) e / learningTime else 1)

    WorkItems.flatten(workItems)
      .filter(i => stateFilter.apply(i.statusName))
      .toDF
      .withColumn("createdWeek", Spark.toIntervalStart(interval)('created))
      .groupBy('statusAuthor.as('author))
      .agg(min('createdWeek).as('min), max('createdWeek).as('max))
      .withColumn("range", range('min, 'max))
      .select('author, explode('range).as('ts))
      .withColumn(
        "experience",
        count('ts)
          .over(
            Window
              .orderBy('ts)
              .partitionBy('author)
              .rowsBetween(Window.unboundedPreceding, Window.currentRow))
      )
      .select('author, 'ts, experience('experience).as('experienceFactor))
      .groupBy('ts)
      .agg(
        countDistinct('author).as('authors),
        sum('experienceFactor).as('totalExperience))
      .withColumn("experienceFactor", 'totalExperience / 'authors)
  }

  def countByState(workItems: Seq[WorkItem], interval: ChronoUnit) = {
    val range = udf(Time.range(interval, _, _))
    val nowIfEmpty = udf((i: Timestamp) => if (i == null) Timestamp.from(Instant.now()) else i)

    WorkItems
      .flatten(workItems)
      .toDF
      .orderBy('id, 'statusCreated)
      .select('id, 'statusCreated, 'statusName)
      .withColumn(
        "statusChanged",
        first('statusCreated)
          .over(
            Window
              .orderBy('statusCreated)
              .partitionBy('id)
              .rowsBetween(Window.currentRow + 1, Window.unboundedFollowing)
          )
      )
      .withColumn("statusChanged", nowIfEmpty('statusChanged))
      .withColumn("createdWeek", Spark.toIntervalStart(interval)('statusCreated))
      .withColumn("changedWeek", Spark.toIntervalStart(interval)('statusChanged))
      .withColumn("range", range('createdWeek, 'changedWeek))
      .select('statusName, explode('range).as("week"))
      .groupBy('week, 'statusName)
      .count()
  }

  def countByStateMovingAverage(workItems: Seq[WorkItem], duration: Long, interval: ChronoUnit) = {
    countByState(workItems, interval)
      .withColumn(
        "moving_average",
        avg('count)
          .over(
            Window
              .orderBy('week)
              .partitionBy('statusName)
              .rowsBetween(Window.currentRow - duration, Window.currentRow)
          ))
      .select('week, 'statusName, 'moving_average)
  }

}
