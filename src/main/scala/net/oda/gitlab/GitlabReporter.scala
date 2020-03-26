package net.oda.gitlab

import java.sql.Timestamp
import java.time.temporal.ChronoUnit

import net.oda.Spark
import net.oda.Spark.session.implicits._
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.expressions.Window.partitionBy
import org.apache.spark.sql.functions._

object GitlabReporter {

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

  def commitsStats(commits: Seq[CommitRecord], interval: ChronoUnit) = {
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

  def mergeRequestsStats(
                          mergeRequests: Seq[MergeRequestRecord],
                          projects: Seq[Project],
                          targetBranch: String,
                          interval: ChronoUnit) = {
    val mergeRequestsDf = mergeRequests.toDF()
    val projectsDf = projects.toDF()
    val namespaceExtractor = udf((name: String, fullName: String) => Project.extractNamespace(name, fullName))

    mergeRequestsDf.join(projectsDf, 'projectId === projectsDf("id"))
      .withColumn("ts", Spark.toIntervalStart(interval)('createdAt))
      .withColumn("namespace", namespaceExtractor('name, 'name_with_namespace))
      .filter('targetBranch === targetBranch)
      .groupBy('ts, 'author, 'state, 'namespace)
      .count()
  }

  def mergeRequestsMovingAverage(
                                  mergeRequests: Seq[MergeRequestRecord],
                                  targetBranch: String,
                                  interval: ChronoUnit) = {
    mergeRequests
      .toDF()
      .withColumn("ts", Spark.toIntervalStart(interval)('createdAt))
      .filter('targetBranch === targetBranch)
      .groupBy('ts, 'state)
      .count()
      .withColumn(
        "moving_average",
        avg('count)
          .over(
            Window
              .orderBy('ts)
              .partitionBy('state)))
      .select('ts, 'state, 'moving_average)
  }

  def mergeRequestsCfd(mergeRequests: Seq[MergeRequestRecord], interval: ChronoUnit) = {
    val mergeRequestsDf = mergeRequests
      .toDF()

    mergeRequestsDf.orderBy('createdAt.desc) show()
    //    val opened = mergeRequestsDf
    //      .withColumn("created", Spark.toIntervalStart(interval)('createdAt))
    //      .filter('targetBranch === "develop")
    //      .groupBy('created, 'state)
    //      .count()
    //      .withColumn(
    //        "opened_count",
    //        sum('count)
    //          .over(
    //            Window
    //              .orderBy('created)
    //              .rowsBetween(Window.unboundedPreceding, Window.currentRow))
    //      )
    //      .drop('count)
    //
    //    opened.show()

    //    val merged = mergeRequestsDf
    //      .withColumn("created", Spark.toIntervalStart(interval)('createdAt))
    //      .filter('targetBranch === "develop")
    //      .filter('state === "merged")
    //      .groupBy('created)
    //      .count()
    //      .withColumn(
    //        "merged_count",
    //        sum('count)
    //          .over(
    //            Window
    //              .orderBy('created)
    //              .rowsBetween(Window.unboundedPreceding, Window.currentRow))
    //      )
    //      .drop('count)
    //      .withColumnRenamed("created", "merge_created")
    //
    //    val either = udf((l: Timestamp, r: Timestamp) => if (l == null) r else l)
    //    val joined = opened
    //      .join(merged, 'created === merged("merge_created"), "outer")
    //      .select(either('created, 'merge_created).as("created"), 'opened_count, 'merged_count)
    //
    //    joined.show(200)
  }
}
