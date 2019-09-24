package net.oda.rep

import java.sql.Timestamp

import net.oda.Mappers._
import net.oda.{Config, Spark}
import net.oda.Spark.session.implicits._
import net.oda.model.{WorkItem, WorkItemStatusHistory}
import org.apache.spark.sql.Row
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions._

case class WorkItemStatus(id: String, `type`: String, created: Timestamp, status: String, flow: String)

object CFDReporter {

  val validFlows = List(
    List("to do", "in progress", "in review", "ready to test", "in testing", "done"),
    List("to do", "in progress", "in review", "done"),
    List("to do", "in progress", "ready to test", "in testing", "done"),
    List("to do", "in progress", "in testing", "done"),
    List("to do", "in progress", "done"),
    List("to do", "done"))

  val stripStatusHistory = (history: List[WorkItemStatusHistory]) => {
    val formatted = history.sortBy(_.created.getTime).map(_.name.toLowerCase)

    validFlows
      .find(f => formatted.endsWith(f))
      .map(f => history.takeRight(f.size).sortBy(_.created.getTime))
      .getOrElse(List.empty)
  }

  val matchCategory = (item: WorkItem) => item.`type`.toLowerCase match {
    case "story" => true
    case "bug" => true
    case _ => false
  }

  val flowDesc = (flow: List[String]) => flow.foldLeft("")(_ + "/" + _)

  val getLongOption = (row: Row, idx: Int) => if (row.isNullAt(idx)) None else Some(row.getLong(idx))

  case class CfdItem(created: String, counts: Map[String, Long])

  val currentOrPrevLongOption: (Row, Int, Option[CfdItem], String) => Option[Long] = (row: Row, idx: Int, prev: Option[CfdItem], key: String) =>
    getLongOption(row, idx)
      .orElse(prev.map(_.counts(key)))

  val forwardFill = (cols: Array[String]) => (acc: List[CfdItem], row: Row) => {
    CfdItem(
      row.getString(0),
      (1 to cols.length - 1)
        .foldLeft(
          Map.empty[String, Long]
        )
        (
          (a, i) => a + (cols(i) -> currentOrPrevLongOption(row, i, acc.headOption, cols(i)).getOrElse(0L))
        )) :: acc
  }

  def generate(workItems: List[WorkItem]) = {
    val cfd = Spark.ctx.parallelize(workItems)
      .filter(matchCategory)
      .map(i => WorkItem(i.id, i.name, i.`type`, i.priority, i.created, i.closed, i.createdBy, stripStatusHistory(i.statusHistory)))
      .filter(!_.statusHistory.isEmpty)
      .flatMap(i => i.statusHistory.map(h => WorkItemStatus(i.id, i.`type`, weekStart(h.created), h.name, flowDesc(i.statusHistory.map(_.name)))))
      .toDF
      .groupBy('created, 'status)
      .count
      .withColumn(
        "cummulativeCount",
        sum('count)
          .over(
            Window
              .partitionBy('status)
              .orderBy('created)
              .rowsBetween(Window.unboundedPreceding, Window.currentRow)))
      .select(date_format('created, "yyyy-MM-dd").as("created"), 'status, 'cummulativeCount)
      .groupBy('created)
      .pivot('status)
      .sum()
      .orderBy('created)

    val cfdFilled = cfd.collect()
      .foldLeft(List[CfdItem]())(forwardFill(cfd.columns))

    Spark.ctx.parallelize(cfdFilled)
      .toDF()
      .select('created, explode('counts))
      .groupBy('created)
      .pivot('key)
      .sum()
      .orderBy('created)
      .repartition(1)
      .write
      .format("csv")
      .mode("overwrite")
      .option("header", true)
      .save(Config.getProp("reports.location").getOrElse(() => "./") + "/cfd")
  }
}
