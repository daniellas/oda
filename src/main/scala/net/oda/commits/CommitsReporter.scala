package net.oda.commits

import java.time.temporal.ChronoUnit

import net.oda.{Spark, Time}
import net.oda.Spark.session.implicits._
import org.apache.spark.sql.expressions.Window.partitionBy
import org.apache.spark.sql.functions._


object CommitsReporter {

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

  def commitsStats(commits: Seq[CommitRecord],
                   interval: ChronoUnit) = {
    commits
      .toDF()
      .withColumn("created", Spark.toIntervalStart(interval)('createdAt))
      .groupBy('created, 'namespace, 'project, 'committer)
      .agg(
        sum('additions).as("additions"),
        sum('deletions).as("deletions"),
        sum('total).as("total")
      )
      .withColumn("effective", 'additions - 'deletions)
  }

}
