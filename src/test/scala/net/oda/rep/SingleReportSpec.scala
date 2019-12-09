package net.oda.rep

import java.time.LocalDate
import java.time.temporal.ChronoUnit

import com.paulgoldbaum.influxdbclient.Parameter.Precision
import com.typesafe.scalalogging.Logger
import net.oda.cfd.{CfdInflux, CfdReporter}
import net.oda.influx.InfluxDb
import net.oda.jira.JiraData
import net.oda.jira.JiraData.location
import net.oda.{Config, IT}
import org.scalatest.FreeSpec

import scala.concurrent.Await
import scala.concurrent.duration._

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
            CfdReporter.countAggregate,
            _))
      .andThen(CfdInflux.toPointsOfInts("cfd-count", _, projectKey, "All stories and bugs", project.entryState, project.finalState, ChronoUnit.WEEKS.name()))
      .andThen(InfluxDb.db.bulkWrite(_, Precision.MILLISECONDS))
      .andThen(Await.result(_, 100 minute))
      .apply(location(projectKey))
  }

}
