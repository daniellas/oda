package net.oda

import java.sql.{Date, Timestamp}
import java.time.{DayOfWeek, ZoneId, ZonedDateTime}
import java.time.temporal.ChronoUnit

import net.oda.data.jira.Issue
import net.oda.model.{WorkItem, WorkItemStatusHistory}

object Mappers {

  implicit def zonedDateTimeToTimestamp(dt: ZonedDateTime) = Timestamp.valueOf(dt.toLocalDateTime)

  implicit def timestampToZonedDateTime(ts: Timestamp) = ZonedDateTime.from(ts.toInstant.atZone(ZoneId.systemDefault()))

  def weekStart(dt: ZonedDateTime) = dt.truncatedTo(ChronoUnit.DAYS).`with`(DayOfWeek.MONDAY)

  val jiraIssueToWorkItem = (issue: Issue) => WorkItem(
    issue.key,
    issue.fields.summary,
    issue.fields.issuetype.name,
    issue.fields.priority.name,
    issue.fields.created,
    issue.fields.resolutiondate.map(zonedDateTimeToTimestamp),
    s"${issue.fields.reporter.displayName} (${issue.fields.reporter.key})",
    issue.changelog.histories
      .flatMap(h => h.items.map(i => (h.created, i.fieldId, i.toStr)))
      .filter(_._2.exists("status".equals))
      .map(i => WorkItemStatusHistory(i._1, i._3.orNull)))
}
