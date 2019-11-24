package net.oda.cfd

import java.sql.Timestamp

import com.paulgoldbaum.influxdbclient.Point
import org.apache.spark.sql.{Dataset, Row}

object CfdInflux {
  def toPoints(
                dataset: Dataset[Row],
                projectKey: String,
                qualifier: String,
                entryState: String,
                finalState: String,
                interval: String): Array[Point] = {
    dataset
      .collect
      .map(r => Point("cfd", r.getAs[Timestamp](CfdReporter.timeCol).getTime)
        .addTag("project", projectKey)
        .addTag("qualifier", qualifier)
        .addTag("interval", interval)
        .addField(CfdReporter.ctCol, r.getAs[Long](CfdReporter.ctCol))
        .addField(CfdReporter.thCol, r.getAs[Double](CfdReporter.thCol))
        .addField(CfdReporter.wipCol, r.getAs[Long](CfdReporter.wipCol))
        .addField(entryState, r.getAs[Long](entryState))
        .addField(finalState, r.getAs[Long](finalState))
      )
  }
}
