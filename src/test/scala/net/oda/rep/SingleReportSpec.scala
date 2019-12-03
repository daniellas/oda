package net.oda.rep

import java.sql.Timestamp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

import com.typesafe.scalalogging.Logger
import net.oda.cfd.{CfdInflux, CfdReporter}
import net.oda.jira.JiraData
import net.oda.{Config, IT, Spark}
import org.apache.spark.ml.linalg.{DenseVector, Vectors}
import org.scalatest.FreeSpec

import scala.concurrent.Await
import scala.concurrent.duration._
import net.oda.Spark.session.implicits._
import org.apache.spark.ml.feature.Normalizer
import org.apache.spark.sql.functions._

class SingleReportsSpec extends FreeSpec {
  val log = Logger(classOf[SingleReportsSpec])

  s"Generate" taggedAs (IT) in {
    val projectKey = "CRYP"
    val project = Config.props.jira.projects(projectKey)

    val vectors = JiraData
      .loadAsWorkItems(project.estimateMapping.get)
      .andThen(
        CfdReporter
          .generate(
            projectKey,
            LocalDate.MIN,
            Seq("Story", "Bug").contains,
            _ => true,
            project.referenceFlow,
            project.entryState,
            project.finalState,
            project.stateMapping,
            ChronoUnit.WEEKS,
            CfdReporter.countAggregate,
            _))
      .apply(JiraData.location(projectKey))
      .map(r => (
        r.getAs[Timestamp](CfdReporter.timeCol),
        Vectors.dense(
          r.getAs[Long](CfdReporter.wipCol),
          r.getAs[Long](CfdReporter.ctCol),
          r.getAs[Double](CfdReporter.thCol)))
      )
      .select('_1.as(CfdReporter.timeCol), '_2.as("vectors"))

    val normalized = new Normalizer()
      .setInputCol("vectors")
      .setOutputCol("normVectors")
      .transform(vectors)
      .drop('vectors)
      .map(r => (
        r.getTimestamp(0),
        r.getAs[DenseVector](1).apply(0),
        r.getAs[DenseVector](1).apply(1),
        r.getAs[DenseVector](1).apply(2)))
      .select(
        '_1.as(CfdReporter.timeCol),
        '_2.as(CfdReporter.wipCol),
        '_3.as(CfdReporter.ctCol),
        '_4.as(CfdReporter.thCol))

    CfdInflux.toPointsNormalized("cfd-normalized",normalized,projectKey,"")
  }

}
