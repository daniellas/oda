package net.oda.rep

import java.time.temporal.ChronoUnit

import com.typesafe.scalalogging.Logger
import net.oda.{Config, IT}
import org.scalatest.FreeSpec

import scala.concurrent.Await
import scala.concurrent.duration._

case class CfdSpec(qualifier: String, typesFilter: String => Boolean, priosFilter: String => Boolean)

class ReportsGeneratorSpec extends FreeSpec {
  val log = Logger(classOf[SingleReportsSpec])
  val intervals = Seq(ChronoUnit.WEEKS, ChronoUnit.DAYS)
  val cfdSpecs = Seq(
    CfdSpec("All stories and bugs", Seq("Story", "Bug").contains, _ => true),
    CfdSpec("All stories", Seq("Story").contains, _ => true),
    CfdSpec("All bugs", Seq("Bug").contains, _ => true),
    CfdSpec("Critical bugs", "Bug".equals, "Critical".equals))
  val devStateFilter = (state: String) => !Seq("Backlog", "Upcoming", "Done").contains(state)

  s"Generate" taggedAs (IT) in {
    Config.props.jira.projects.foreach(p => {
      Await.result(ReportsGenerator.workItemsChangelog(p._1, ChronoUnit.DAYS), 100 second)
      Await.result(ReportsGenerator.jiraCountByTypePriority(p._1, ChronoUnit.DAYS, p._2.stateMapping), 100 second)
      Await.result(ReportsGenerator.jiraCountDistinctAuthors(p._1, ChronoUnit.DAYS, devStateFilter, "DEV/QA"), 100 second)
      Await.result(ReportsGenerator.teamProductivityFactor(p._1, devStateFilter, ChronoUnit.WEEKS, 12D), 100 second)

      intervals.foreach(i => {
        cfdSpecs
          .map(s => ReportsGenerator
            .jiraCfd(p._1, p._2.entryState, p._2.finalState, p._2.stateMapping, p._2.referenceFlow, s.typesFilter, s.priosFilter, i, s.qualifier))
          .foreach(Await.result(_, 100 second))
      })
    })
  }

}
