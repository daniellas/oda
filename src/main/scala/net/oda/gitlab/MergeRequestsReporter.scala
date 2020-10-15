package net.oda.gitlab

import java.sql.Timestamp
import java.time.temporal.ChronoUnit

import net.oda.Spark.session.implicits._
import net.oda.gitlab.GitlabReporter.{readCategories, readRoles}
import net.oda.{Spark, Time}
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions.{avg, count, countDistinct, explode, max, stddev_pop, sum, udf, _}

object MergeRequestsReporter {
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
                             projects: Seq[Project],
                             targetBranch: String,
                             interval: ChronoUnit) = {
    val duration = udf((start: Timestamp, end: Timestamp) => Time.daysBetweenTimestamps(start, end))
    val mergeRequestsDf = selectLastMergeRequestState(mergeRequests)
    val projectsDf = projects.toDF().withColumnRenamed("id", "prId")

    mergeRequestsDf
      .withColumnRenamed("id", "mrId")
      .join(projectsDf, 'projectId === projectsDf("prId"))
      .filter('targetBranch === targetBranch)
      .filter('state === "merged")
      .withColumn("ts", Spark.toIntervalStart(interval)('createdAt))
      .withColumn("duration", duration('createdAt, 'mergedAt))
      .withColumn("rootNamespace", projectRootNamespace('name_with_namespace))
      .groupBy('ts, 'rootNamespace)
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

  def mergeRequestsByState(
                            mergeRequests: Seq[MergeRequestRecord],
                            projects: Seq[Project],
                            targetBranch: String,
                            interval: ChronoUnit) = {
    val mergeRequestsDf = selectLastMergeRequestState(mergeRequests)
    val projectsDf = projects.toDF().withColumnRenamed("id", "prId")

    mergeRequestsDf
      .filter('targetBranch === targetBranch)
      .withColumnRenamed("id", "mrId")
      .join(projectsDf, 'projectId === projectsDf("prId"))
      .withColumn("ts", Spark.toIntervalStart(interval)('createdAt))
      .withColumn("rootNamespace", projectRootNamespace('name_with_namespace))
      .groupBy('ts, 'state, 'rootNamespace)
      .count()
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

  val projectRootNamespace = udf(Project.extractRootNamespace(_))

  def mergeRequestsByAuthor(
                             mergeRequests: Seq[MergeRequestRecord],
                             targetBranch: String,
                             interval: ChronoUnit) = {
    val mergeRequestsDf = MergeRequestsReporter.selectLastMergeRequestState(mergeRequests)

    mergeRequestsDf
      .withColumn("ts", Spark.toIntervalStart(interval)('createdAt))
      .filter('targetBranch === targetBranch)
      .groupBy('ts, 'author)
      .count()
  }

  def mergeRequestsMovingAverage(
                                  mergeRequests: Seq[MergeRequestRecord],
                                  projects: Seq[Project],
                                  targetBranch: String,
                                  interval: ChronoUnit) = {
    val mergeRequestsDf = MergeRequestsReporter.selectLastMergeRequestState(mergeRequests)
    val projectsDf = projects.toDF().withColumnRenamed("id", "prId")

    mergeRequestsDf
      .withColumnRenamed("id", "mrId")
      .join(projectsDf, 'projectId === projectsDf("prId"))
      .withColumn("ts", Spark.toIntervalStart(interval)('createdAt))
      .withColumn("rootNamespace", projectRootNamespace('name_with_namespace))
      .groupBy('ts, 'state, 'rootNamespace)
      .count()
      .withColumn(
        "moving_average",
        avg('count)
          .over(
            Window
              .orderBy('ts)
              .partitionBy('state)))
      .select('ts, 'state, 'moving_average, 'rootNamespace)
  }

  def mergeRequestsByProjectRole(
                                  mergeRequests: Seq[MergeRequestRecord],
                                  projects: Seq[Project],
                                  targetBranch: String,
                                  interval: ChronoUnit) = {
    val roles = readRoles()
    val mergeRequestsDf = MergeRequestsReporter.selectLastMergeRequestState(mergeRequests)

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
    val mergeRequestsDf = MergeRequestsReporter.selectLastMergeRequestState(mergeRequests)

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

  def mergeRequestStatsByProject(
                                  mergeRequests: Seq[MergeRequestRecord],
                                  projects: Seq[Project],
                                  targetBranch: String,
                                  interval: ChronoUnit) = {
    val mergeRequestsDf = MergeRequestsReporter.selectLastMergeRequestState(mergeRequests)
    val projectsDf = projects.toDF().withColumnRenamed("id", "prjId")

    mergeRequestsDf.join(projectsDf, 'projectId === projectsDf("prjId"))
      .filter('targetBranch === targetBranch)
      .filter('state === "merged")
      .withColumn("ts", Spark.toIntervalStart(interval)('createdAt))
      .groupBy('ts, 'name_with_namespace)
      .agg(
        count('prjId).as("count"),
        sum('userNotesCount).as("comments"))
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
