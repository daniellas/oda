package net.oda.commits

import java.sql.Timestamp
import java.time.temporal.ChronoUnit

import com.paulgoldbaum.influxdbclient.{Point, Record}
import net.oda.Config
import net.oda.Time._
import net.oda.gitlab.{Commit, Project}
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

object CommitsInflux {
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
}
