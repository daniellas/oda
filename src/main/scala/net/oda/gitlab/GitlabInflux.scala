package net.oda.gitlab

import java.sql.Timestamp
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import com.paulgoldbaum.influxdbclient.{Point, Record}
import net.oda.Time
import net.oda.Time._
import net.oda.influx.InfluxDb
import org.apache.spark.sql.DataFrame

import scala.concurrent.ExecutionContext.Implicits.global

case class CommitRecord(
                         createdAt: Timestamp,
                         project: String,
                         namespace: String,
                         committer: String,
                         additions: Int,
                         deletions: Int,
                         total: Int,
                         effective: Int,
                         projectId: Int
                       ) {
  def mapCommitter(mapper: String => String) = CommitRecord(
    this.createdAt,
    this.project,
    this.namespace,
    mapper(this.committer),
    this.additions,
    this.deletions,
    this.total,
    this.effective,
    this.projectId
  )
}

case object CommitRecord {
  def of(record: Record) = new CommitRecord(
    toTimestamp(parseZonedDateTime(record("time").asInstanceOf[String])),
    record("project").asInstanceOf[String],
    record("namespace").asInstanceOf[String],
    record("committer").asInstanceOf[String],
    record("additions").asInstanceOf[BigDecimal].toInt,
    record("deletions").asInstanceOf[BigDecimal].toInt,
    record("total").asInstanceOf[BigDecimal].toInt,
    record("effective").asInstanceOf[BigDecimal].toInt,
    record("projectId").asInstanceOf[String].toInt
  )

  def extractRootNamespace(namespace: String) = namespace.split("/")(0).trim
}

case class MergeRequestRecord(
                               createdAt: Timestamp,
                               id: Int,
                               projectId: Int,
                               author: String,
                               state: String,
                               sourceBranch: String,
                               targetBranch: String,
                               userNotesCount: Int,
                               mergedAt: Option[Timestamp],
                               updatedAt: Long)

case object MergeRequestRecord {
  def of(record: Record) = new MergeRequestRecord(
    toTimestamp(parseZonedDateTime(record("time").asInstanceOf[String])),
    record("id").asInstanceOf[String].toInt,
    record("project_id").asInstanceOf[String].toInt,
    record("author").asInstanceOf[String],
    record("state").asInstanceOf[String],
    record("source_branch").asInstanceOf[String],
    record("target_branch").asInstanceOf[String],
    record("user_notes_count").asInstanceOf[BigDecimal].toInt,
    Option(record("merged_at_ts").asInstanceOf[BigDecimal])
      .map(_.toLongExact)
      .filterNot(_ == 0L)
      .map(Time.toZonedDateTime)
      .map(Time.toTimestamp),
    record("updated_at").asInstanceOf[BigDecimal].toLong)
}

object GitlabInflux {
  def toCommitsPoints(pcs: Seq[(Project, Commit)]) = pcs
    .map(pc => Point("commits", pc._2.created_at.toInstant.toEpochMilli)
      .addTag("project", pc._1.name_with_namespace)
      .addTag("namespace", pc._1.namespace())
      .addTag("committer", pc._2.committer_email)
      .addTag("projectId", pc._1.id.toString)
      .addField("additions", pc._2.stats.additions)
      .addField("deletions", -1 * pc._2.stats.deletions)
      .addField("effective", pc._2.stats.additions - pc._2.stats.deletions)
      .addField("total", pc._2.stats.total))

  def toNamespaceActivityRankPoints(df: DataFrame, interval: ChronoUnit) = {
    df.collect()
      .map(r => Point("namespace_activity_rank", r.getTimestamp(0).toInstant.toEpochMilli)
        .addTag("interval", interval.name())
        .addTag("namespace", r.getString(1))
        .addField("additions", r.getLong(2))
        .addField("deletions", r.getLong(3))
        .addField("total", r.getLong(4))
        .addField("effective", r.getLong(5))
        .addField("additionsRank", r.getInt(6))
        .addField("deletionsRank", r.getInt(7))
        .addField("totalRank", r.getInt(8))
        .addField("effectiveRank", r.getInt(9)))
  }


  def toReposActivityRankPoints(df: DataFrame, interval: ChronoUnit) = {
    df.collect()
      .map(r => Point("repos_activity_rank", r.getTimestamp(0).toInstant.toEpochMilli)
        .addTag("interval", interval.name())
        .addTag("project", r.getString(1))
        .addField("additions", r.getLong(2))
        .addField("deletions", r.getLong(3))
        .addField("total", r.getLong(4))
        .addField("effective", r.getLong(5))
        .addField("additionsRank", r.getInt(6))
        .addField("deletionsRank", r.getInt(7))
        .addField("totalRank", r.getInt(8))
        .addField("effectiveRank", r.getInt(9)))
  }

  def toCommittersActivityPoints(df: DataFrame, interval: ChronoUnit) = {
    df.collect()
      .map(r => Point("committers_activity", r.getTimestamp(0).toInstant.toEpochMilli)
        .addTag("interval", interval.name())
        .addTag("committer", r.getString(1))
        .addField("additions", r.getLong(2))
        .addField("deletions", r.getLong(3))
        .addField("total", r.getLong(4))
        .addField("effective", r.getLong(5))
        .addField("additionsRank", r.getInt(6))
        .addField("deletionsRank", r.getInt(7))
        .addField("totalRank", r.getInt(8))
        .addField("effectiveRank", r.getInt(9)))
  }

  def toCommitsStatsPoints(df: DataFrame, interval: ChronoUnit) = {
    df.collect()
      .map(r => Point("commits_stats", r.getTimestamp(0).toInstant.toEpochMilli)
        .addTag("interval", interval.name())
        .addTag("namespace", r.getString(1))
        .addTag("project", r.getString(2))
        .addTag("committer", r.getString(3))
        .addField("additions", r.getLong(4))
        .addField("deletions", r.getLong(5))
        .addField("total", r.getLong(6))
        .addField("effective", r.getLong(7)))
  }

  def toCommitsSummaryPoints(df: DataFrame, interval: ChronoUnit) = {
    df.collect()
      .map(r => Point("commits_summary", r.getTimestamp(0).toInstant.toEpochMilli)
        .addTag("interval", interval.name())
        .addTag("rootNamespace", r.getString(1))
        .addField("additions", r.getLong(2))
        .addField("deletions", r.getLong(3))
        .addField("total", r.getLong(4))
        .addField("effective", r.getLong(5)))
  }

  def toCommitsByNamespacePoints(df: DataFrame, interval: ChronoUnit) = {
    df.collect()
      .map(r => Point("commits_by_namespace", r.getTimestamp(0).toInstant.toEpochMilli)
        .addTag("interval", interval.name())
        .addTag("namespace", r.getString(1))
        .addField("additions", r.getLong(2))
        .addField("deletions", r.getLong(3))
        .addField("total", r.getLong(4))
        .addField("effective", r.getLong(5)))
  }

  def toActiveCommittersPoints(df: DataFrame, interval: ChronoUnit) = {
    df.collect()
      .map(r => Point("active_committers", r.getTimestamp(0).toInstant.toEpochMilli)
        .addTag("interval", interval.name())
        .addTag("rootNamespace", r.getString(1))
        .addField("count", r.getLong(2)))
  }

  def toCommittersLifeSpanStatsPiont(df: DataFrame, interval: ChronoUnit) = {
    df.collect()
      .map(r => Point("committer_life_span_stats", r.getTimestamp(0).toInstant.toEpochMilli)
        .addTag("interval", interval.name())
        .addField("count", r.getLong(1))
        .addField("total", r.getLong(2))
        .addField("avg", r.getDouble(3))
        .addField("movAvg", r.getDouble(4)))
  }

  def loadCommits() = InfluxDb.db.query("select * from commits")
    .map(_.series.head.records.map(CommitRecord.of))

  def toMergeRequestsPoints(mrs: Seq[MergeRequest]) = mrs
    .map(mr => Point("merge_requests", mr.created_at.toInstant.toEpochMilli)
      .addTag("id", mr.id.toString)
      .addTag("project_id", mr.project_id.toString)
      .addTag("author", mr.author.username)
      .addTag("state", mr.state)
      .addTag("source_branch", mr.source_branch)
      .addTag("target_branch", mr.target_branch)
      .addField("updated_at", mr.updated_at.toInstant.toEpochMilli)
      .addField("user_notes_count", mr.user_notes_count)
      .addField("merged_at_ts", mr.merged_at.map(_.toInstant.toEpochMilli).getOrElse(0L))
      .addField("duration_days", mr.merged_at.map(Time.daysBetweenTimestamps(mr.created_at, _)).getOrElse(Time.daysBetweenTimestamps(mr.created_at, ZonedDateTime.now()))))

  def toMergeRequestsByStatePoints(df: DataFrame, interval: ChronoUnit) = {
    df.collect()
      .map(r => Point("merge_requests_by_state", r.getTimestamp(0).toInstant.toEpochMilli)
        .addTag("interval", interval.name())
        .addTag("state", r.getString(1))
        .addTag("root_namespace", r.getString(2))
        .addField("count", r.getLong(3)))
  }

  def toMergeRequestsByAuthorPoints(df: DataFrame, interval: ChronoUnit) = {
    df.collect()
      .map(r => Point("merge_requests_by_author", r.getTimestamp(0).toInstant.toEpochMilli)
        .addTag("interval", interval.name())
        .addTag("author", r.getString(1))
        .addField("count", r.getLong(2)))
  }

  def toMergeRequestsMovingAveragePoints(df: DataFrame, interval: ChronoUnit) = {
    df.collect()
      .map(r => Point("merge_requests_moving_average", r.getTimestamp(0).toInstant.toEpochMilli)
        .addTag("interval", interval.name())
        .addTag("state", r.getString(1))
        .addField("moving_average", r.getDouble(2))
        .addTag("root_namespace", r.getString(3)))
  }

  def toMergeRequestsCommentsPoints(df: DataFrame, interval: ChronoUnit) = {
    df.collect()
      .map(r => Point("merge_requests_comments", r.getTimestamp(0).toInstant.toEpochMilli)
        .addTag("interval", interval.name())
        .addTag("author", r.getString(1))
        .addField("sum", r.getLong(2)))
  }

  def toMergeRequestsDurationPoints(df: DataFrame, interval: ChronoUnit) = {
    df.collect()
      .map(r => Point("merge_requests_duration", r.getTimestamp(0).toInstant.toEpochMilli)
        .addTag("interval", interval.name())
        .addTag("root_namespace", r.getString(1))
        .addField("duration_avg", r.getDouble(2))
        .addField("duration_max", r.getLong(3))
        .addField("duration_std_dev", r.getDouble(4)))
  }

  def toMergeRequestsAuthorsRatioPoints(df: DataFrame, interval: ChronoUnit) = {
    df.collect()
      .map(r => Point("merge_requests_authors_ratio", r.getTimestamp(0).toInstant.toEpochMilli)
        .addTag("interval", interval.name())
        .addField("ratio", r.getDouble(1))
        .addField("moving_average", r.getDouble(2)))
  }

  def loadMergeRequests() = InfluxDb.db.query("select * from merge_requests")
    .map(_.series.head.records.map(MergeRequestRecord.of))

  def toCommitsStatsByProjectRolePoints(df: DataFrame, interval: ChronoUnit) = {
    df.collect()
      .map(r => Point("commits_stats_by_project_role", r.getTimestamp(0).toInstant.toEpochMilli)
        .addTag("interval", interval.name())
        .addTag("role", r.getString(1))
        .addField("additions", r.getLong(2))
        .addField("deletions", r.getLong(3)))
  }

  def toCommitsStatsByProjectRoleNamespacePoints(df: DataFrame, interval: ChronoUnit) = {
    df.collect()
      .map(r => Point("commits_stats_by_project_role_namespace", r.getTimestamp(0).toInstant.toEpochMilli)
        .addTag("interval", interval.name())
        .addTag("role", r.getString(1))
        .addTag("namespace", r.getString(2))
        .addField("additions", r.getLong(3))
        .addField("deletions", r.getLong(4)))
  }

  def toCommitsStatsByProjectCategoryPoints(df: DataFrame, interval: ChronoUnit) = {
    df.collect()
      .map(r => Point("commits_stats_by_project_category", r.getTimestamp(0).toInstant.toEpochMilli)
        .addTag("interval", interval.name())
        .addTag("category", r.getString(1))
        .addField("additions", r.getLong(2))
        .addField("deletions", r.getLong(3)))
  }

  def toCommitsStatsByProjectCategoryNamespacePoints(df: DataFrame, interval: ChronoUnit) = {
    df.collect()
      .map(r => Point("commits_stats_by_project_category_namespace", r.getTimestamp(0).toInstant.toEpochMilli)
        .addTag("interval", interval.name())
        .addTag("category", r.getString(1))
        .addTag("namespace", r.getString(2))
        .addField("additions", r.getLong(3))
        .addField("deletions", r.getLong(4)))
  }

  def toMergeRequestsByProjectRolePoints(df: DataFrame, interval: ChronoUnit) = {
    df.collect()
      .map(r => Point("merge_requests_by_project_role", r.getTimestamp(0).toInstant.toEpochMilli)
        .addTag("interval", interval.name())
        .addTag("role", r.getString(1))
        .addField("count", r.getLong(2)))
  }

  def toMergeRequestsByProjectCategoryPoints(df: DataFrame, interval: ChronoUnit) = {
    df.collect()
      .map(r => Point("merge_requests_by_project_category", r.getTimestamp(0).toInstant.toEpochMilli)
        .addTag("interval", interval.name())
        .addTag("category", r.getString(1))
        .addField("count", r.getLong(2)))
  }

  def toMergeRequestStatsByProjectPoints(df: DataFrame, interval: ChronoUnit) = {
    df.collect()
      .map(r => Point("merge_request_stats_by_project", r.getTimestamp(0).toInstant.toEpochMilli)
        .addTag("interval", interval.name())
        .addTag("project", r.getString(1))
        .addField("count", r.getLong(2))
        .addField("comments", r.getLong(3)))
  }

}
