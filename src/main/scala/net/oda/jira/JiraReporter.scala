package net.oda.jira

import java.sql.Timestamp
import java.time.temporal.ChronoUnit

import net.oda.Spark.session.implicits._
import net.oda.Time
import net.oda.workitem.WorkItem

case class CountItem(ts: Timestamp, `type`: String, priority: String)

object JiraReporter {
  def countByTypePriority(
                           workItems: Seq[WorkItem],
                           stateMapping: Map[String, String],
                           interval: ChronoUnit) = {
    workItems
      .map(i => CountItem(Time.interval(interval, i.created), i.`type`, i.priority))
      .toDF
      .groupBy('type, 'priority, 'ts)
      .count
  }
}
