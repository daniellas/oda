package net.oda.cfd

import java.sql.Timestamp

import com.paulgoldbaum.influxdbclient.Point
import net.oda.cfd.CfdReporter.changeCol
import org.apache.spark.sql.{Dataset, Row}

object CfdInflux {
  def toPointsOfInts(
                      measurement: String,
                      dataset: Dataset[Row],
                      projectKey: String,
                      qualifier: String,
                      entryState: String,
                      finalState: String,
                      interval: String
                    ): Array[Point] = {
    dataset
      .collect
      .map(r => Point(measurement, r.getAs[Timestamp](CfdReporter.timeCol).getTime)
        .addTag("project", projectKey)
        .addTag("qualifier", qualifier)
        .addTag("interval", interval)
        .addField(CfdReporter.ctCol, r.getAs[Long](CfdReporter.ctCol))
        .addField(CfdReporter.thCol, r.getAs[Double](CfdReporter.thCol))
        .addField(CfdReporter.currentWipCol, r.getAs[Long](CfdReporter.currentWipCol))
        .addField(CfdReporter.wipCol, r.getAs[Double](CfdReporter.wipCol))
        .addField(entryState, r.getAs[Long](entryState))
        .addField(finalState, r.getAs[Long](finalState))
        .addField(changeCol(entryState), r.getAs[Long](changeCol(entryState)))
        .addField(changeCol(finalState), r.getAs[Long](changeCol(finalState)))
      )
  }

  def toPointsOfDecimals(
                          measurement: String,
                          dataset: Dataset[Row],
                          projectKey: String,
                          qualifier: String,
                          entryState: String,
                          finalState: String,
                          interval: String
                        ): Array[Point] = {
    dataset
      .collect
      .map(r => Point(measurement, r.getAs[Timestamp](CfdReporter.timeCol).getTime)
        .addTag("project", projectKey)
        .addTag("qualifier", qualifier)
        .addTag("interval", interval)
        .addField(CfdReporter.ctCol, r.getAs[Long](CfdReporter.ctCol))
        .addField(CfdReporter.thCol, r.getAs[Double](CfdReporter.thCol))
        .addField(CfdReporter.currentWipCol, r.getAs[Double](CfdReporter.currentWipCol))
        .addField(CfdReporter.wipCol, r.getAs[Double](CfdReporter.wipCol))
        .addField(entryState, r.getAs[Double](entryState))
        .addField(finalState, r.getAs[Double](finalState))
        .addField(changeCol(entryState), r.getAs[Double](changeCol(entryState)))
        .addField(changeCol(finalState), r.getAs[Double](changeCol(finalState)))
      )
  }

  def toPointsNormalized(
                          measurement: String,
                          dataset: Dataset[Row],
                          projectKey: String,
                          qualifier: String,
                          interval: String
                        ): Array[Point] = {
    dataset
      .collect
      .map(r => Point(measurement, r.getAs[Timestamp](CfdReporter.timeCol).getTime)
        .addTag("project", projectKey)
        .addTag("qualifier", qualifier)
        .addTag("interval", interval)
        .addField(CfdReporter.ctCol, r.getAs[Long](CfdReporter.ctCol))
        .addField(CfdReporter.thCol, r.getAs[Double](CfdReporter.thCol))
        .addField(CfdReporter.currentWipCol, r.getAs[Double](CfdReporter.currentWipCol))
      )
  }

}
