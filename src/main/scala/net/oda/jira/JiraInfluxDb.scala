package net.oda.jira

import java.sql.Timestamp

import com.paulgoldbaum.influxdbclient.Point
import org.apache.spark.sql.{Dataset, Row}

object JiraInfluxDb {
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

}
