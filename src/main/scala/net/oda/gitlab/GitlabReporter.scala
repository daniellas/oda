package net.oda.gitlab

import java.sql.Timestamp
import java.time.temporal.ChronoUnit

import net.oda.Spark.session.implicits._
import net.oda.Time.toZonedDateTime
import net.oda.{Config, Spark}
import org.apache.spark.sql.SaveMode
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.expressions.Window.partitionBy
import org.apache.spark.sql.functions._

object GitlabReporter {

  def readRoles() = {
    Spark.session.read.textFile(Config.dataLocation + "/gitlab-classification/roles.txt")
  }

  def readCategories() = {
    Spark.session.read.textFile(Config.dataLocation + "/gitlab-classification/categories.txt")
  }

  def namespaceActivityRank(
                             commits: Seq[CommitRecord],
                             interval: ChronoUnit) = {
    commits
      .toDF()
      .withColumn("created", Spark.toIntervalStart(interval)('createdAt))
      .groupBy('created, 'namespace)
      .agg(
        sum('additions).as("additions"),
        sum('deletions).as("deletions"),
        sum('total).as("total")
      )
      .withColumn("effective", 'additions - 'deletions)
      .withColumn("additionsRank", dense_rank().over(partitionBy('created).orderBy('additions)))
      .withColumn("deletionsRank", dense_rank().over(partitionBy('created).orderBy('deletions)))
      .withColumn("totalRank", dense_rank().over(partitionBy('created).orderBy('total)))
      .withColumn("effectiveRank", dense_rank().over(partitionBy('created).orderBy('effective)))

  }

  def reposActivityRank(
                         commits: Seq[CommitRecord],
                         interval: ChronoUnit) = {
    commits
      .toDF()
      .withColumn("created", Spark.toIntervalStart(interval)('createdAt))
      .groupBy('created, 'project)
      .agg(
        sum('additions).as("additions"),
        sum('deletions).as("deletions"),
        sum('total).as("total")
      )
      .withColumn("effective", 'additions - 'deletions)
      .withColumn("additionsRank", dense_rank().over(partitionBy('created).orderBy('additions)))
      .withColumn("deletionsRank", dense_rank().over(partitionBy('created).orderBy('deletions)))
      .withColumn("totalRank", dense_rank().over(partitionBy('created).orderBy('total)))
      .withColumn("effectiveRank", dense_rank().over(partitionBy('created).orderBy('effective)))
  }

  def committersActivityRank(
                              commits: Seq[CommitRecord],
                              interval: ChronoUnit) = {
    commits
      .toDF()
      .withColumn("created", Spark.toIntervalStart(interval)('createdAt))
      .groupBy('created, 'committer)
      .agg(
        sum('additions).as("additions"),
        sum('deletions).as("deletions"),
        sum('total).as("total")
      )
      .withColumn("effective", 'additions - 'deletions)
      .withColumn("additionsRank", dense_rank().over(partitionBy('created).orderBy('additions)))
      .withColumn("deletionsRank", dense_rank().over(partitionBy('created).orderBy('deletions)))
      .withColumn("totalRank", dense_rank().over(partitionBy('created).orderBy('total)))
      .withColumn("effectiveRank", dense_rank().over(partitionBy('created).orderBy('effective)))
  }

  def committersLifeSpanStats(commits: Seq[CommitRecord], interval: ChronoUnit) = {
    val duration = udf((start: Timestamp, end: Timestamp) => interval.between(toZonedDateTime(start), toZonedDateTime(end)) + 1)
    val isCommitterValid = udf((committer: String) =>
      !committer.contains("-")
        && !committer.contains("efl.com.pl")
        && !committer.contains("ingbank.pl")
        && !committer.contains("swissborg.com")
        && !committer.contains("tomashanak.com")
        && !committer.contains("gmail.com"))

    val committersDuration = commits
      .toDF()
      .groupBy('committer)
      .agg(
        min('createdAt).as("start"),
        max('createdAt).as("end"))
      .filter(isCommitterValid('committer))
      .filter('start =!= 'end)
      .withColumn("duration", duration('start, 'end))

    committersDuration
      .coalesce(1)
      .write.format("csv").mode(SaveMode.Overwrite)
      .save(s"${Config.dataLocation}/committers_duration.csv")

    committersDuration.withColumn("ts", Spark.toIntervalStart(interval)('start))
      .groupBy('ts)
      .agg(
        count('duration).as("durationCount"),
        sum('duration).as("durationTotal")
      )
      .withColumn("durationAvg", 'durationTotal / 'durationCount)
      .withColumn(
        "durationMovAvg",
        avg('durationAvg)
          .over(
            Window
              .orderBy('ts)))
  }

}
