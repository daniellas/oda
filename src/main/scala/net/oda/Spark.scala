package net.oda

import org.apache.spark.sql.SparkSession

object Spark {
  val session = SparkSession
    .builder()
    .appName("ODA")
    .master("local[*]")
    .getOrCreate
  val ctx = session.sparkContext

}
