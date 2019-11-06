package net.oda.rep.cfd

import java.sql.Timestamp

import com.paulgoldbaum.influxdbclient.Point
import org.apache.spark.sql.{Dataset, Row}

object CFDInfluxDb {
  def toPoints(
               dataset: Dataset[Row],
               measurement: String,
               projectKey: String,
               qualifier: String,
               entryState: String,
               finalState: String): Array[Point] = {
    dataset
      .collect
      .map(r => Point(measurement, r.getAs[Timestamp](CFDReporter.timeCol).getTime)
        .addTag("project", projectKey)
        .addTag("qualifier", qualifier)
        .addField(CFDReporter.ctCol, r.getAs[Long](CFDReporter.ctCol))
        .addField(CFDReporter.thCol, r.getAs[Double](CFDReporter.thCol))
        .addField(CFDReporter.wipCol, r.getAs[Long](CFDReporter.wipCol))
        .addField(entryState, r.getAs[Long](entryState))
        .addField(finalState, r.getAs[Long](finalState))
      )
  }
}
