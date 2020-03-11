package net.oda

import java.sql.Timestamp
import java.time.temporal.ChronoUnit

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.expressions.UserDefinedFunction
import org.apache.spark.sql.functions.udf

import net.oda.Time._

object Spark {
  val session = SparkSession
    .builder()
    .appName("ODA")
    .master("local[*]")
    .getOrCreate
  val ctx = session.sparkContext

  val toIntervalStart: ChronoUnit => UserDefinedFunction = interval => udf(Time.interval.apply(interval, _))
  val weekDay: UserDefinedFunction = udf(Time.weekDay(_))
}
