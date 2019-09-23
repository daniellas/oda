package net.oda

import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.SparkSession

object Spark {
  Logger.getLogger("org").setLevel(Level.OFF)
  Logger.getLogger("akka").setLevel(Level.OFF)
  Logger.getLogger("io.netty").setLevel(Level.OFF)

  val session = SparkSession
    .builder()
    .appName("ODA")
    .master("local[*]")
    .getOrCreate()
  val ctx = session.sparkContext
}
