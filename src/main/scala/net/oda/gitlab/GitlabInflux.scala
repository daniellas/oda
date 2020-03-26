package net.oda.gitlab

import java.sql.Timestamp
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import com.paulgoldbaum.influxdbclient.{Point, Record}
import net.oda.{Config, Time}
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
                         effective: Int
                       )

case object CommitRecord {
  def of(record: Record) = new CommitRecord(
    toTimestamp(parseZonedDateTime(record("time").asInstanceOf[String])),
    record("project").asInstanceOf[String],
    record("namespace").asInstanceOf[String],
    record("committer").asInstanceOf[String],
    record("additions").asInstanceOf[BigDecimal].toInt,
    record("deletions").asInstanceOf[BigDecimal].toInt,
    record("total").asInstanceOf[BigDecimal].toInt,
    record("effective").asInstanceOf[BigDecimal].toInt
  )
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
                               mergedAt: Option[Timestamp])

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
      .map(Time.toTimestamp)
  )
}

object GitlabInflux {
  def toCommitsPoints(pcs: Seq[(Project, Commit)]) = pcs
    .map(pc => Point("commits", pc._2.created_at.toInstant.toEpochMilli)
      .addTag("project", pc._1.name_with_namespace)
      .addTag("namespace", pc._1.namespace())
      .addTag("committer", Config.mapEmail(pc._2.committer_email))
      .addField("additions", pc._2.stats.additions)
      .addField("deletions", pc._2.stats.deletions)
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
      .addField("user_notes_count", mr.user_notes_count)
      .addField("merged_at_ts", mr.merged_at.map(_.toInstant.toEpochMilli).getOrElse(0L))
      .addField("duration_days", mr.merged_at.map(Time.daysBetweenTimestamps(mr.created_at, _)).getOrElse(Time.daysBetweenTimestamps(mr.created_at, ZonedDateTime.now()))))

  def toMergeRequestsStatsPoints(df: DataFrame, interval: ChronoUnit) = {
    df.collect()
      .map(r => Point("merge_requests_stats", r.getTimestamp(0).toInstant.toEpochMilli)
        .addTag("interval", interval.name())
        .addTag("author", r.getString(1))
        .addTag("state", r.getString(2))
        .addTag("project", r.getString(3))
        .addField("count", r.getLong(4)))
  }

  def toMergeRequestsMovingAveragePoints(df: DataFrame, interval: ChronoUnit) = {
    df.collect()
      .map(r => Point("merge_requests_moving_average", r.getTimestamp(0).toInstant.toEpochMilli)
        .addTag("interval", interval.name())
        .addTag("state", r.getString(1))
        .addField("moving_average", r.getDouble(2)))
  }

  def loadMergeRequests() = InfluxDb.db.query("select * from merge_requests")
    .map(_.series.head.records.map(MergeRequestRecord.of))


}
