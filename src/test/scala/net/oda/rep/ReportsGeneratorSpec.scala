package net.oda.rep

import java.time.temporal.ChronoUnit

import com.typesafe.scalalogging.Logger
import net.oda.rep.ReportsGenerator._
import net.oda.{Config, IT}
import org.scalatest.FreeSpec

import scala.concurrent.Await
import scala.concurrent.duration._

case class CfdSpec(qualifier: String, typesFilter: String => Boolean, priosFilter: String => Boolean)

class ReportsGeneratorSpec extends FreeSpec {
  val log = Logger(classOf[SingleReportsSpec])
  val intervals = Seq(ChronoUnit.WEEKS, ChronoUnit.MONTHS)
  val cfdSpecs = Seq(
    CfdSpec("All stories and bugs", Seq("Story", "Bug").contains, _ => true),
    CfdSpec("All stories", Seq("Story").contains, _ => true),
    CfdSpec("All bugs", Seq("Bug").contains, _ => true),
    CfdSpec("Critical bugs", "Bug".equals, "Critical".equals))
  val devStateFilter = (state: String) => !Seq("Backlog", "Upcoming", "Done").contains(state)

  s"Generate" taggedAs (IT) in {
    Config.props.jira.projects.foreach(p => {
      Await.result(workItemsChangelog(p._1, ChronoUnit.DAYS), 100 second)
      Await.result(jiraCountByTypePriority(p._1, ChronoUnit.DAYS, p._2.stateMapping), 100 second)
      Await.result(jiraCountDistinctAuthors(p._1, ChronoUnit.DAYS, devStateFilter, "DEV/QA"), 100 second)
      Await.result(teamProductivityFactor(p._1, devStateFilter, ChronoUnit.WEEKS, 12D), 100 second)

      intervals.foreach(i => {
        jiraCountCfd(p._1, "In Progress", "Done", p._2.stateMapping, p._2.referenceFlow, Seq("Story", "Bug").contains, _ => true, i, "All items from In Progress to Done")
        cfdSpecs
          .foreach(s => {
            Await.result(jiraCountCfd(p._1, p._2.entryState, p._2.finalState, p._2.stateMapping, p._2.referenceFlow, s.typesFilter, s.priosFilter, i, s.qualifier), 100 second)
            Await.result(jiraEstimateCfd(p._1, p._2.entryState, p._2.finalState, p._2.stateMapping, p._2.referenceFlow, s.typesFilter, s.priosFilter, i, s.qualifier), 100 second)
          })
      })
    })
  }

}