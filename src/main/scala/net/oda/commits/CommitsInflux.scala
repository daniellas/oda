package net.oda.commits

import com.paulgoldbaum.influxdbclient.Point
import net.oda.gitlab.{Commit, Project}

object CommitsInflux {
  def toCommitsPoints(pcs: Seq[(Project, Commit)]) = pcs
    .map(pc => Point("commits", pc._2.created_at.toInstant.toEpochMilli)
      .addTag("project", pc._1.name_with_namespace)
      .addTag("committer", pc._2.committer_email)
      .addField("additions", pc._2.stats.additions)
      .addField("deletions", pc._2.stats.deletions)
      .addField("effective", pc._2.stats.additions - pc._2.stats.deletions)
      .addField("total", pc._2.stats.total))
}
