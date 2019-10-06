package net.oda

import org.json4s.DefaultFormats
import org.scalatest.{FreeSpec, Matchers}
import net.oda.Spark.session.implicits._
import org.json4s.jackson.Serialization

class DatFrameToJsonSpec extends FreeSpec with Matchers {

  "should create JSON from DataFrame" in {
    implicit val formats = DefaultFormats

    val dataFrame = (1 to 10)
      .map(i => ("value" + i, i))
      .toDF("name", "value")

    val map = dataFrame
      .collect
      .map(
        row => dataFrame
          .columns
          .foldLeft(Map.empty[String, Any])
          (
            (acc, item) => acc + (item -> row.getAs[Any](item))
          )
      )

    val json = Serialization.write(map)

    println(json)
  }
}
