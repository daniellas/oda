package net.oda.jira

import java.time.LocalDate

case class ProjectVersions(
                            projectId: Int,
                            id: String,
                            name: String,
                            archived: Boolean,
                            released: Boolean,
                            startDate: Option[LocalDate],
                            releaseDate: Option[LocalDate])
