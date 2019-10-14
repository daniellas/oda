package net.oda.model

import java.sql.Timestamp

case class WorkItemStatusHistory(created: Timestamp, name: String)

case class WorkItem(
                     id: String,
                     name: String,
                     `type`: String,
                     priority: String,
                     created: Timestamp,
                     closed: Option[Timestamp],
                     createdBy: String,
                     size: Option[String],
                     statusHistory: Seq[WorkItemStatusHistory])