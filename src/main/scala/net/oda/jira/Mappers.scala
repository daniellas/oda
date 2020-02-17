package net.oda.jira

import net.oda.Time.toTimestamp
import net.oda.workitem.{WorkItem, Status}

object Mappers {

  implicit val jiraIssueToWorkItem = (issue: Issue, estimateCalculator: String => Option[Double]) => {
    val historyItems = issue.changelog.histories
      .flatMap(h => h.items.map(i => (h.created, i.fieldId, i.toStr, h.author)))
    val size = historyItems
      .filter(_._2.exists("customfield_10035".equals))
      .sortBy(_._1.getTime)
      .reverse
      .headOption
      .flatMap(_._3)
    val storyPoints = historyItems
      .filter(_._2.exists("customfield_10014".equals))
      .sortBy(_._1.getTime)
      .reverse
      .headOption
      .flatMap(_._3)
      .filter(!_.isEmpty)
      .map(_.toDouble)
    val epicName = historyItems
      .filter(_._2.exists("customfield_10005".equals))
      .sortBy(_._1.getTime)
      .reverse
      .headOption
      .flatMap(_._3)

    WorkItem(
      issue.key,
      issue.fields.summary,
      issue.fields.issuetype.name,
      issue.fields.priority.name,
      issue.fields.created,
      issue.fields.resolutiondate.map(toTimestamp),
      s"${issue.fields.reporter.displayName}",
      size,
      size.flatMap(estimateCalculator).getOrElse(storyPoints.getOrElse(0.0)),
      historyItems
        .filter(_._2.exists("status".equals))
        .map(i => Status(i._1, i._3.orNull, Some(i._4.displayName))),
      epicName)
  }
}
