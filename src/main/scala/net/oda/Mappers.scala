package net.oda

import net.oda.Time._
import net.oda.data.jira.Issue
import net.oda.model.{WorkItem, WorkItemStatusHistory}

object Mappers {

  val jiraIssueToWorkItem = (issue: Issue) => WorkItem(
    issue.key,
    issue.fields.summary,
    issue.fields.issuetype.name,
    issue.fields.priority.name,
    issue.fields.created,
    issue.fields.resolutiondate.map(toTimestamp),
    s"${issue.fields.reporter.displayName} (${issue.fields.reporter.key})",
    issue.changelog.histories
      .flatMap(h => h.items.map(i => (h.created, i.fieldId, i.toStr)))
      .filter(_._2.exists("status".equals))
      .map(i => WorkItemStatusHistory(i._1, i._3.orNull)))
}
