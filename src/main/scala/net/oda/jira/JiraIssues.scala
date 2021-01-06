package net.oda.jira

import java.time.ZonedDateTime

case class IssueType(id: String, name: String)

case class Priority(name: String)

case class Fields(
                   summary: String,
                   resolutiondate: Option[ZonedDateTime],
                   created: ZonedDateTime,
                   issuetype: IssueType,
                   reporter: Person,
                   priority: Priority)

case class Person(accountId: String, displayName: String)

case class HistoryItem(id: String, author: Person, created: ZonedDateTime, items: List[ChangeItem])

case class ChangeItem(fieldId: Option[String], from: Option[String], fromString: Option[String], to: Option[String], toStr: Option[String])

case class Changelog(histories: List[HistoryItem])

case class Issue(key: String, fields: Fields, changelog: Changelog)

case class JiraIssues(
                       startAt: Integer,
                       maxResults: Integer,
                       total: Integer,
                       issues: List[Issue]) {
}

object JiraIssues {
  def empty = JiraIssues(0, null, null, Nil)

  val status = "status"
  val fixVersions = "fixVersions"
  val validFields = Set(status, fixVersions)

  def trimHistoryItems(issue: Issue) = {
    Issue(
      issue.key,
      issue.fields,
      Changelog(
        issue.changelog.histories.map(h => HistoryItem(
          h.id,
          h.author,
          h.created,
          h.items
            .filter(_.fieldId.exists(validFields.contains))
            .map(i => ChangeItem(
              i.fieldId,
              i.from,
              i.fromString,
              i.to,
              i.toStr
            ))
        )).filterNot(_.items.isEmpty)
      )
    )
  }
}