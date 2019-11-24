package net.oda.jira

import java.sql.Timestamp

import com.paulgoldbaum.influxdbclient.Point
import org.apache.spark.sql.{Dataset, Row}

object JiraInflux {
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

}
