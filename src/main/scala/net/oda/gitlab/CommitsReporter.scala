package net.oda.gitlab

import java.time.temporal.ChronoUnit

import net.oda.Spark
import net.oda.Spark.session.implicits._
import net.oda.gitlab.GitlabReporter.{readCategories, readRoles}
import org.apache.spark.sql.functions.{countDistinct, explode, sum, udf}

object CommitsReporter {
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

  val commitRootNamespace = udf(CommitRecord.extractRootNamespace(_))

  def commitsSummary(commits: Seq[CommitRecord], interval: ChronoUnit) = {

    commits
      .toDF()
      .withColumn("created", Spark.toIntervalStart(interval)('createdAt))
      .withColumn("rootNamespace", commitRootNamespace('namespace))
      .groupBy('created, 'rootNamespace)
      .agg(
        sum('additions).as("additions"),
        sum('deletions).as("deletions"),
        sum('total).as("total")
      )
      .withColumn("effective", 'additions - 'deletions)
  }

  def commitsByNamespace(commits: Seq[CommitRecord], interval: ChronoUnit) = {
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
  }

  def activeCommitters(commits: Seq[CommitRecord], interval: ChronoUnit) = {
    commits
      .toDF()
      .withColumn("created", Spark.toIntervalStart(interval)('createdAt))
      .withColumn("rootNamespace", commitRootNamespace('namespace))
      .groupBy('created, 'rootNamespace)
      .agg(
        countDistinct('committer).as("count")
      )
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

  def commitsStatsByProjectRoleNamespace(projects: Seq[Project], commits: Seq[CommitRecord], interval: ChronoUnit) = {
    val roles = readRoles()
    val commitsDf = commits.toDF()

    projects.toDF()
      .withColumn("tag", explode('tag_list))
      .join(roles, 'tag === roles("value"))
      .select('id, 'tag.as("role"))
      .join(commitsDf, 'id === commitsDf("projectId"))
      .withColumn("ts", Spark.toIntervalStart(interval)('createdAt))
      .groupBy('ts, 'role, 'namespace)
      .agg(
        sum('additions).as("additions"),
        sum('deletions).as("deletions"))
  }

  def commitsStatsByProjectCategory(projects: Seq[Project], commits: Seq[CommitRecord], interval: ChronoUnit) = {
    val categories = readCategories()
    val commitsDf = commits.toDF()

    projects.toDF()
      .withColumn("tag", explode('tag_list))
      .join(categories, 'tag === categories("value"))
      .select('id, 'tag.as("category"))
      .join(commitsDf, 'id === commitsDf("projectId"))
      .withColumn("ts", Spark.toIntervalStart(interval)('createdAt))
      .groupBy('ts, 'category)
      .agg(
        sum('additions).as("additions"),
        sum('deletions).as("deletions"))
  }

  def commitsStatsByProjectCategoryNamespace(projects: Seq[Project], commits: Seq[CommitRecord], interval: ChronoUnit) = {
    val categories = readCategories()
    val commitsDf = commits.toDF()

    projects.toDF()
      .withColumn("tag", explode('tag_list))
      .join(categories, 'tag === categories("value"))
      .select('id, 'tag.as("category"))
      .join(commitsDf, 'id === commitsDf("projectId"))
      .withColumn("ts", Spark.toIntervalStart(interval)('createdAt))
      .groupBy('ts, 'category, 'namespace)
      .agg(
        sum('additions).as("additions"),
        sum('deletions).as("deletions"))
  }
}
