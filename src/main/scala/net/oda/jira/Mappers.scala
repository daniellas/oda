package net.oda.jira

import net.oda.Config
import net.oda.Time.toTimestamp
import net.oda.workitem.{Status, WorkItem}

object Mappers {

  implicit val jiraIssueToWorkItem = (issue: Issue, estimateCalculator: String => Option[Double]) => {
    val historyItems = issue.changelog.histories
      .flatMap(h => h.items
        .map(i => (h.created, i.fieldId, i.toStr, h.author, i.to)))
    val estimate = historyItems
      .filter(_._2.contains(Config.props.jira.estimateField.getOrElse("")))
      .sortBy(_._1.getTime)
      .reverse
      .headOption
      .flatMap(_._3)
    val storyPoints = historyItems
      .filter(_._2.contains(Config.props.jira.storyPointsField.getOrElse("")))
      .sortBy(_._1.getTime)
      .reverse
      .headOption
      .flatMap(_._3)
      .filter(!_.isEmpty)
      .map(_.toDouble)
    val epicName = historyItems
      .filter(_._2.contains(Config.props.jira.epicNameField.getOrElse("")))
      .sortBy(_._1.getTime)
      .reverse
      .headOption
      .flatMap(_._3)
    val version = historyItems
      .filter(_._2.contains(JiraIssues.fixVersions))
      .sortBy(_._1.getTime)
      .reverse
      .headOption
      .flatMap(_._5)
      .map(_.toInt)

    WorkItem(
      issue.key,
      issue.fields.summary,
      issue.fields.issuetype.name,
      issue.fields.priority.name,
      issue.fields.created,
      issue.fields.resolutiondate.map(toTimestamp),
      s"${issue.fields.reporter.displayName}",
      estimate,
      estimate.flatMap(estimateCalculator).getOrElse(storyPoints.getOrElse(0.0)),
      historyItems
        .filter(_._2.contains(JiraIssues.status))
        .map(i => Status(i._1, i._3.orNull, Some(i._4.displayName))),
      epicName,
      version)
  }
}
