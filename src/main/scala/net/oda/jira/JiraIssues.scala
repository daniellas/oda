package net.oda.jira

import java.time.ZonedDateTime

case class IssueType(id: String, name: String)

case class Priority(name: String)

case class Fields(
                   resolutiondate: Option[ZonedDateTime],
                   created: ZonedDateTime,
                   issuetype: IssueType,
                   summary: String,
                   reporter: Person,
                   priority: Priority)

case class Person(key: String, displayName: String)

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
}