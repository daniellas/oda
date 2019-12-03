package net.oda.rep

import java.sql.Timestamp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

import com.paulgoldbaum.influxdbclient.Parameter.Precision
import com.typesafe.scalalogging.Logger
import net.oda.cfd.{CfdInflux, CfdReporter}
import net.oda.jira.JiraData
import net.oda.{Config, IT, Spark}
import org.apache.spark.ml.linalg.{DenseVector, Vectors}
import org.scalatest.FreeSpec

import scala.concurrent.Await
import scala.concurrent.duration._
import net.oda.Spark.session.implicits._
import net.oda.cfd.CfdInflux.toPointsOfInts
import net.oda.influx.InfluxDb
import net.oda.jira.JiraData.location
import org.apache.spark.ml.feature.Normalizer
import org.apache.spark.sql.functions._

class SingleReportsSpec extends FreeSpec {
  val log = Logger(classOf[SingleReportsSpec])

  s"Generate" taggedAs (IT) in {
    val projectKey = "CRYP"
    val project = Config.props.jira.projects(projectKey)

    JiraData
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
            CfdReporter.sumEstimateAggregate,
            _))
      .andThen(CfdInflux.toPointsOfDecimals("cfd-estimate", _, projectKey, "All stories and bugs", project.entryState, project.finalState, ChronoUnit.WEEKS.name()))
      .andThen(InfluxDb.db.bulkWrite(_, Precision.MILLISECONDS))
      .andThen(Await.result(_, 100 minute))
      .apply(location(projectKey))
  }

}
