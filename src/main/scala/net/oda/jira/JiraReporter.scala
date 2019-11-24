package net.oda.jira

import java.sql.Timestamp
import java.time.temporal.ChronoUnit

import org.apache.spark.sql.functions._

import net.oda.Spark.session.implicits._
import net.oda.Time
import net.oda.workitem.{WorkItem, WorkItems}

object JiraReporter {

  case class TypePriority(ts: Timestamp, `type`: String, priority: String)

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
}
