package net.oda.gitlab

import java.sql.Timestamp
import java.time.temporal.ChronoUnit

import net.oda.{Config, Spark, Time}
import net.oda.Spark.session.implicits._
import net.oda.Time.toZonedDateTime
import org.apache.spark.ml.feature.{LabeledPoint, StandardScaler}
import org.apache.spark.ml.linalg.Vectors
import org.apache.spark.ml.regression.LinearRegression
import org.apache.spark.mllib.linalg.distributed.RowMatrix
import org.apache.spark.mllib.stat.Statistics
import org.apache.spark.sql.SaveMode
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
      .map(_.mapCommitter(Config.mapEmail))
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

  def selectLastMergeRequestState(mergeRequests: Seq[MergeRequestRecord]) = {
    mergeRequests
      .toDF()
      .withColumn(
        "row",
        row_number()
          .over(
            Window
              .partitionBy('id)
              .orderBy('updatedAt.desc)))
      .where('row === 1)
  }

  //  def mergeRequestsByState(
  //                          mergeRequests: Seq[MergeRequestRecord],
  //                          projects: Seq[Project],
  //                          targetBranch: String,
  //                          interval: ChronoUnit) = {
  //    val mergeRequestsDf = selectLastMergeRequestState(mergeRequests)
  //    val projectsDf = projects.toDF()
  //    val namespaceExtractor = udf((name: String, fullName: String) => Project.extractNamespace(name, fullName))
  //
  //    mergeRequestsDf.join(projectsDf, 'projectId === projectsDf("id"))
  //      .withColumn("ts", Spark.toIntervalStart(interval)('createdAt))
  //      .withColumn("namespace", namespaceExtractor('name, 'name_with_namespace))
  //      .filter('targetBranch === targetBranch)
  //      .groupBy('ts, 'state)
  //      .count()
  //  }

  def mergeRequestsByState(
                            mergeRequests: Seq[MergeRequestRecord],
                            targetBranch: String,
                            interval: ChronoUnit) = {
    val mergeRequestsDf = selectLastMergeRequestState(mergeRequests)

    mergeRequestsDf
      .withColumn("ts", Spark.toIntervalStart(interval)('createdAt))
      .filter('targetBranch === targetBranch)
      .groupBy('ts, 'state)
      .count()
  }

  def mergeRequestsByAuthor(
                            mergeRequests: Seq[MergeRequestRecord],
                            targetBranch: String,
                            interval: ChronoUnit) = {
    val mergeRequestsDf = selectLastMergeRequestState(mergeRequests)

    mergeRequestsDf
      .withColumn("ts", Spark.toIntervalStart(interval)('createdAt))
      .filter('targetBranch === targetBranch)
      .groupBy('ts, 'author)
      .count()
  }

  def mergeRequestsMovingAverage(
                                  mergeRequests: Seq[MergeRequestRecord],
                                  targetBranch: String,
                                  interval: ChronoUnit) = {
    selectLastMergeRequestState(mergeRequests)
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


  def mergeRequestsComments(
                             mergeRequests: Seq[MergeRequestRecord],
                             targetBranch: String,
                             interval: ChronoUnit) = {
    selectLastMergeRequestState(mergeRequests)
      .withColumn("ts", Spark.toIntervalStart(interval)('createdAt))
      .filter('targetBranch === targetBranch)
      .groupBy('ts, 'author)
      .sum("userNotesCount")
  }

  def mergeRequestsDuration(
                             mergeRequests: Seq[MergeRequestRecord],
                             targetBranch: String,
                             interval: ChronoUnit) = {
    val duration = udf((start: Timestamp, end: Timestamp) => Time.daysBetweenTimestamps(start, end))

    selectLastMergeRequestState(mergeRequests)
      .withColumn("ts", Spark.toIntervalStart(interval)('createdAt))
      .filter('targetBranch === targetBranch)
      .filter('state === "merged")
      .withColumn("duration", duration('createdAt, 'mergedAt))
      .groupBy('ts)
      .agg(
        avg('duration).as("duration_avg"),
        max('duration).as("duration_max"),
        stddev_pop('duration).as("std_dev"))
  }

  def mergeRequestsAuthorsRatio(
                                 mergeRequests: Seq[MergeRequestRecord],
                                 targetBranch: String,
                                 interval: ChronoUnit) = {
    val mergeRequestsDf = selectLastMergeRequestState(mergeRequests)
    val authorsCount = mergeRequestsDf
      .withColumn("ts_authors", Spark.toIntervalStart(interval)('createdAt))
      .filter('targetBranch === targetBranch)
      .filter('state === "merged")
      .groupBy('ts_authors)
      .agg(countDistinct('author).as("authors"))

    val mergeRequestsCount = mergeRequestsDf
      .withColumn("ts_mrs", Spark.toIntervalStart(interval)('createdAt))
      .filter('targetBranch === targetBranch)
      .filter('state === "merged")
      .groupBy('ts_mrs)
      .count()

    authorsCount.join(mergeRequestsCount, 'ts_authors === mergeRequestsCount("ts_mrs"))
      .withColumn("ratio", 'count / 'authors)
      .select('ts_authors.as("ts"), 'ratio)
      .withColumn(
        "moving_average",
        avg('ratio)
          .over(
            Window
              .orderBy('ts)))
  }

  private def readRoles() = {
    Spark.session.read.textFile(Config.dataLocation + "/gitlab-classification/roles.txt")
  }

  private def readCategories() = {
    Spark.session.read.textFile(Config.dataLocation + "/gitlab-classification/categories.txt")
  }

  def commitsStatsByProjectRole(projects: Seq[Project], commits: Seq[CommitRecord], interval: ChronoUnit) = {
    val roles = readRoles()
    val commitsDf = commits.toDF()

    projects.toDF()
      .withColumn("tag", explode('tag_list))
      .join(roles, 'tag === roles("value"))
      .select('id, 'tag.as("role"))
      .join(commitsDf, 'id === commitsDf("projectId"))
      .withColumn("ts", Spark.toIntervalStart(interval)('createdAt))
      .groupBy('ts, 'role)
      .agg(
        sum('additions).as("additions"),
        sum('deletions).as("deletions"))
  }

  def commitsStatsByProjectCatogory(projects: Seq[Project], commits: Seq[CommitRecord], interval: ChronoUnit) = {
    val categories = readCategories()
    val commitsDf = commits.toDF()

    projects.toDF()
      .withColumn("tag", explode('tag_list))
      .join(categories, 'tag === categories("value"))
      .select('id, 'tag.as("role"))
      .join(commitsDf, 'id === commitsDf("projectId"))
      .withColumn("ts", Spark.toIntervalStart(interval)('createdAt))
      .groupBy('ts, 'role)
      .agg(
        sum('additions).as("additions"),
        sum('deletions).as("deletions"))
  }

  def mergeRequestsByProjectRole(
                                  mergeRequests: Seq[MergeRequestRecord],
                                  projects: Seq[Project],
                                  targetBranch: String,
                                  interval: ChronoUnit) = {
    val roles = readRoles()
    val mergeRequestsDf = selectLastMergeRequestState(mergeRequests)

    val projectsTags = projects
      .toDF()
      .withColumn("tag", explode('tag_list))
      .join(roles, 'tag === roles("value"))
      .select('id, 'tag.as("role"))

    mergeRequestsDf.join(projectsTags, 'projectId === projectsTags("id"))
      .filter('targetBranch === targetBranch)
      .filter('state === "merged")
      .withColumn("ts", Spark.toIntervalStart(interval)('createdAt))
      .groupBy('ts, 'role)
      .count()
  }

  def mergeRequestsByProjectCategory(
                                      mergeRequests: Seq[MergeRequestRecord],
                                      projects: Seq[Project],
                                      targetBranch: String,
                                      interval: ChronoUnit) = {
    val categories = readCategories()
    val mergeRequestsDf = selectLastMergeRequestState(mergeRequests)

    val projectsTags = projects
      .toDF()
      .withColumn("tag", explode('tag_list))
      .join(categories, 'tag === categories("value"))
      .select('id, 'tag.as("category"))

    mergeRequestsDf.join(projectsTags, 'projectId === projectsTags("id"))
      .filter('targetBranch === targetBranch)
      .filter('state === "merged")
      .withColumn("ts", Spark.toIntervalStart(interval)('createdAt))
      .groupBy('ts, 'category)
      .count()
  }

  def mergeRequestsCorrelation(
                                mergeRequests: Seq[MergeRequestRecord]
                              ) = {
    val weekOfYear = udf((ts: Timestamp) => Time.yearWeek(ts))

    val res = mergeRequests
      .toDF()
      .withColumn("ts", Spark.toIntervalStart(ChronoUnit.DAYS)('createdAt))
      .groupBy('ts)
      .count()
      .withColumn("weekOfYear", weekOfYear('ts))
      .groupBy('weekOfYear)
      .agg(
        sum('count).as("sum"),
        count('count).as("count"))
      .withColumn("mean", 'sum / 'count)

    res.orderBy('weekOfYear).show(53)

    //    val labels = res
    //      .map(r => LabeledPoint(r.getInt(2).toDouble, Vectors.dense(Array(r.getLong(1).toDouble))))
    //
    //    val lrModel = new LinearRegression()
    //      .setMaxIter(10)
    //      .setRegParam(0.3)
    //      .setElasticNetParam(0.8)
    //      .fit(labels)
    //
    //    (1 to 52)
    //      .map(_.toDouble)
    //      .foreach(w => println(lrModel.predict(Vectors.dense(Array(w)))))
  }
}
