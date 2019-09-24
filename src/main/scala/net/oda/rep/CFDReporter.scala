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

case class CfdItem(created: String, cummulativeCounts: Map[Int, Long])

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

  def putTuple(m: Map[Int, Long], t: (Int, Long)) = m + (t._1 -> t._2)

  def getCounts(row: Row, prev: Option[CfdItem]): Map[Int, Long] = (1 to 9)
    .toArray
    .map(i => getLongOption(row, i).map(c => (i, c)).getOrElse(() => (i, 0L)))
    .foldLeft(Map.empty[Int, Long])(putTuple)

  val forwardFill = (acc: List[CfdItem], row: Row) => acc match {
    case Nil => CfdItem(
      row.getString(0),
      getCounts(row, None)) :: acc
    case xs => CfdItem(
      row.getString(0),
      getCounts(row, xs.headOption)) :: acc
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
        sum('count).over(
          Window
            .partitionBy('status)
            .orderBy('created)
            .rowsBetween(Window.unboundedPreceding, Window.currentRow)))
      .select(date_format('created, "yyyy-MM-dd").as("created"), 'status, 'cummulativeCount)
      .groupBy('created)
      .pivot('status)
      .sum()
      .orderBy('created)
      .coalesce(1)

    val cfdFilled = cfd.collect()
      .foldLeft(List[CfdItem]())(forwardFill)

    Spark.ctx.parallelize(cfdFilled)
      .toDF()
      .orderBy('created)
      .show

    //    cfd.write
    //      .format("csv")
    //      .mode("overwrite")
    //      .option("header", true)
    //      .save(Config.getProp("reports.location").getOrElse(() => "./") + "/cfd")
  }
}
