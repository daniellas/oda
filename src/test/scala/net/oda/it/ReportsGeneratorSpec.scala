package net.oda.it

import java.time.temporal.ChronoUnit

import com.typesafe.scalalogging.Logger
import net.oda.jira.{JiraData, JiraReporter}
import net.oda.rep.ReportsGenerator
import net.oda.{Config, IT}
import org.scalatest.FreeSpec

import scala.concurrent.Await
import scala.concurrent.duration._

case class CfdSpec(qualifier: String, typesFilter: String => Boolean, priosFilter: String => Boolean)

class ReportsGeneratorSpec extends FreeSpec {
  val log = Logger(classOf[ReportsGeneratorSpec])
  val intervals = Seq(ChronoUnit.WEEKS, ChronoUnit.DAYS)
  val cfdSpecs = Seq(
    CfdSpec("All stories and bugs", Seq("Story", "Bug").contains, _ => true),
    CfdSpec("All stories", Seq("Story").contains, _ => true),
    CfdSpec("All bugs", Seq("Bug").contains, _ => true),
    CfdSpec("Critical bugs", "Bug".equals, "Critical".equals))
  val devStateFilter = (state: String) => !Seq("Backlog", "Upcoming").contains(state)

  Config.props.jira.projects.foreach(p => {
    intervals.foreach(i => {
      Await.result(ReportsGenerator.jiraCountByTypePriority(p._1, i, p._2.stateMapping), 100 second)
      Await.result(ReportsGenerator.jiraCountDistinctAuthors(p._1, i, devStateFilter, "DEV/QA"), 100 second)
      cfdSpecs
        .map(s => ReportsGenerator
          .jiraCfd(p._1, p._2.entryState, p._2.finalState, p._2.stateMapping, p._2.referenceFlow, s.typesFilter, s.priosFilter, i, s.qualifier))
        .foreach(Await.result(_, 100 second))
    })
  })

}
