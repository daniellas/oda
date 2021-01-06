package net.oda.report

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import com.typesafe.scalalogging.Logger
import net.oda.report.ReportsGenerator._
import net.oda.{Config, IT}
import org.scalatest.FreeSpec

import scala.concurrent.Await
import scala.concurrent.duration._

case class CfdSpec(
                    qualifier: String,
                    typesFilter: String => Boolean,
                    priosFilter: String => Boolean,
                    entryState: String,
                    finalState: String,
                    interval: ChronoUnit)

class ReportsGeneratorSpec extends FreeSpec {
  val log = Logger(classOf[ReportsGeneratorSpec])
  val cfdSpecs = Seq(
    CfdSpec("All stories and bugs", Seq("Story", "Bug").contains, _ => true, "In Progress", "Done", ChronoUnit.WEEKS),
    CfdSpec("All stories", Seq("Story").contains, _ => true, "In Progress", "Done", ChronoUnit.WEEKS),
    CfdSpec("All bugs", Seq("Bug").contains, _ => true, "In Progress", "Done", ChronoUnit.WEEKS),
    CfdSpec("Critical bugs", "Bug".equals, "Critical".equals, "In Progress", "Done", ChronoUnit.DAYS)
  )
  val devStateFilter = (state: String) => !Seq("Backlog", "Upcoming", "Done").contains(state)

  s"Generate" taggedAs (IT) in {
    //    generateJiraReports()
    generateGitlabMergeRequestsReports()
    //    generateGitlabCommitsReports()
  }

  def generateJiraReports() = {
    Config.props.jira.projects.foreach(p => {
      Await.result(workItemsCountByState(p._1, ChronoUnit.WEEKS), 10 minutes)
      Await.result(workItemsCountByStateMovingAverage(p._1, 14, ChronoUnit.WEEKS), 10 minutes)
      Await.result(jiraCountByTypePriority(p._1, ChronoUnit.WEEKS, p._2.stateMapping), 10 minutes)
      Await.result(jiraCountDistinctAuthors(p._1, ChronoUnit.MONTHS, devStateFilter, "DEV/QA"), 10 minutes)
      Await.result(teamProductivityFactor(p._1, devStateFilter, ChronoUnit.WEEKS, 12D), 10 minutes)

      cfdSpecs
        .foreach(s =>
          Await.result(jiraCountCfd(p._1, s.entryState, s.finalState, p._2.stateMapping, p._2.referenceFlow, s.typesFilter, s.priosFilter, s.interval, s.qualifier), 10 minutes)
        )
    })
  }

  def generateGitlabMergeRequestsReports() = {
    Await.result(ReportsGenerator.mergeRequestsByState("develop", ChronoUnit.WEEKS), 5 minutes)
    Await.result(ReportsGenerator.mergeRequestsByAuthor("develop", ChronoUnit.WEEKS), 5 minutes)
    Await.result(ReportsGenerator.mergeRequestsComments("develop", ChronoUnit.WEEKS), 5 minutes)
    Await.result(ReportsGenerator.mergeRequestsMovingAverage("develop", ChronoUnit.WEEKS), 5 minutes)
    Await.result(ReportsGenerator.mergeRequestsDuration("develop", ChronoUnit.WEEKS), 5 minutes)
    Await.result(ReportsGenerator.mergeRequestsByProjectRole("develop", ChronoUnit.WEEKS), 5 minutes)
    Await.result(ReportsGenerator.mergeRequestsByProjectCategory("develop", ChronoUnit.WEEKS), 5 minutes)
    Await.result(ReportsGenerator.mergeRequestStatsByProject("develop", ChronoUnit.WEEKS), 5 minutes)
  }

  def generateGitlabCommitsReports() = {
    Await.result(ReportsGenerator.activeCommitters(ChronoUnit.WEEKS), 5 minutes)
    Await.result(ReportsGenerator.commitsSummary(ChronoUnit.WEEKS), 5 minutes)
    Await.result(ReportsGenerator.commitsStats(ChronoUnit.WEEKS), 5 minutes)
    Await.result(ReportsGenerator.commitsStatsByProjectRole(ChronoUnit.WEEKS), 5 minutes)
    Await.result(ReportsGenerator.commitsStatsByProjectCategory(ChronoUnit.WEEKS), 5 minutes)
    Await.result(ReportsGenerator.commitsStatsByProjectRoleNamespace(ChronoUnit.WEEKS), 5 minutes)
    Await.result(ReportsGenerator.commitsStatsByProjectCategoryNamespace(ChronoUnit.WEEKS), 5 minutes)
    Await.result(ReportsGenerator.commitsByNamespace(ChronoUnit.WEEKS), 5 minutes)
    Await.result(ReportsGenerator.committersLifeSpan(ChronoUnit.MONTHS), 5 minutes)
    Await.result(ReportsGenerator.committersLifeSpanStats(ChronoUnit.MONTHS), 5 minutes)
  }

}
