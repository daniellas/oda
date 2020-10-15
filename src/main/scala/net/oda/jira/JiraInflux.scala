package net.oda.jira

import java.sql.Timestamp

import com.paulgoldbaum.influxdbclient.Point
import net.oda.workitem.WorkItemStatus
import org.apache.spark.sql.{Dataset, Row}

object JiraInflux {
  def workItemsChangelog(
                          workItems: Seq[WorkItemStatus],
                          projectKey: String,
                          interval: String
                        ): Array[Point] = {
    workItems
      .map(i => Point("work_items_change_log", i.statusCreated.getTime)
        .addTag("project", projectKey)
        .addTag("interval", interval)
        .addTag("id", i.id)
        .addTag("type", i.`type`)
        .addTag("priority", i.priority)
        .addTag("changedBy", i.statusAuthor.getOrElse(""))
        .addTag("status", i.statusName)
        .addTag("epic", i.epicName.getOrElse("No epic"))
        .addField("count", 1)
      ).toArray
  }

  def countByTypePriorityPoints(
                                 dataset: Dataset[Row],
                                 projectKey: String,
                                 interval: String
                               ): Array[Point] = {
    dataset
      .collect
      .map(r => Point("work_items_count", r.getAs[Timestamp]("ts").getTime)
        .addTag("project", projectKey)
        .addTag("interval", interval)
        .addTag("type", r.getAs[String]("type"))
        .addTag("priority", r.getAs[String]("priority"))
        .addField("count", r.getAs[Long]("count"))
      )
  }

  def countDistinctAuthorsPoints(
                                  dataset: Dataset[Row],
                                  projectKey: String,
                                  interval: String,
                                  qualifier: String
                                ): Array[Point] = {
    dataset
      .collect
      .map(r => Point("state_distinct_authors", r.getAs[Timestamp](0).getTime)
        .addTag("project", projectKey)
        .addTag("interval", interval)
        .addTag("qualifier", qualifier)
        .addField("count", r.getAs[Long](1))
      )
  }

  def teamProductivityFactor(
                              dataset: Dataset[Row],
                              projectKey: String,
                              interval: String
                            ): Array[Point] = {
    dataset
      .collect
      .map(r => Point("team_productivity_factor", r.getAs[Timestamp](0).getTime)
        .addTag("project", projectKey)
        .addTag("interval", interval)
        .addField("authors", r.getAs[Long](1))
        .addField("totalExperience", r.getAs[Double](2))
        .addField("experienceFactor", r.getAs[Double](3))
      )

  }

  def workItemsCountByStatePoints(
                                   dataset: Dataset[Row],
                                   projectKey: String,
                                   interval: String
                                 ): Array[Point] = {
    dataset
      .collect
      .map(r => Point("work_items_count_by_state", r.getAs[Timestamp](0).getTime)
        .addTag("project", projectKey)
        .addTag("interval", interval)
        .addTag("status", r.getString(1))
        .addField("count", r.getLong(2))
      )

  }

  def workItemsCountByStateMovingAveragePoints(
                                                dataset: Dataset[Row],
                                                projectKey: String,
                                                interval: String
                                              ): Array[Point] = {
    dataset
      .collect
      .map(r => Point("work_items_count_by_state_moving_average", r.getAs[Timestamp](0).getTime)
        .addTag("project", projectKey)
        .addTag("interval", interval)
        .addTag("status", r.getString(1))
        .addField("moving_average", r.getDouble(2))
      )

  }

}
