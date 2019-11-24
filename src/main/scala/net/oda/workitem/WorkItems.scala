package net.oda.workitem

import java.sql.Timestamp

case class Status(created: Timestamp, name: String, author: Option[String] = None) {}

case class WorkItem(
                     id: String,
                     name: String,
                     `type`: String,
                     priority: String,
                     created: Timestamp,
                     closed: Option[Timestamp],
                     createdBy: String,
                     size: Option[String],
                     estimate: Double,
                     statusHistory: Seq[Status])

case class WorkItemStatus(
                           id: String,
                           name: String,
                           `type`: String,
                           priority: String,
                           created: Timestamp,
                           closed: Option[Timestamp],
                           createdBy: String,
                           size: Option[String],
                           estimate: Double,
                           statusCreated: Timestamp,
                           statusName: String,
                           statusAuthor: Option[String])

object WorkItems {
  def flatten(workItems: Seq[WorkItem]): Seq[WorkItemStatus] = workItems.flatMap(i => i.statusHistory.map(s =>
    WorkItemStatus(
      i.id,
      i.name,
      i.`type`,
      i.priority,
      i.created,
      i.closed,
      i.createdBy,
      i.size,
      i.estimate,
      s.created,
      s.name,
      s.author)))
}