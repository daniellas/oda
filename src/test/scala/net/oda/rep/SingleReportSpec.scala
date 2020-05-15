package net.oda.rep

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import com.typesafe.scalalogging.Logger
import net.oda.cfd.CfdReporter
import net.oda.{Config, FileIO, IT}
import net.oda.gitlab.{GitlabClient, GitlabInflux, GitlabReporter}
import net.oda.jira.JiraData
import net.oda.rep.ReportsGenerator.{jiraCountByTypePriority, jiraCountCfd, jiraCountDistinctAuthors, jiraEstimateCfd, teamProductivityFactor, workItemsChangelog, workItemsDuration}
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization
import org.scalatest.FreeSpec

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class SingleReportsSpec extends FreeSpec {
  val log = Logger(classOf[SingleReportsSpec])
  val months = 10 * 12;
  implicit val jsonFormats = DefaultFormats
  val stateMapping = Map("Invalid" -> "Done", "IN REVIW" -> "In Review")
  val referenceFlow = Map(
    "In Progress" -> 2,
    "In Review" -> 3,
    "Ready to test" -> 4,
    "In testing" -> 5,
    "Done" -> 6)
  val projectKey = "HFT"
  val entryState = "In Progress"
  val finalState = "Done"

  s"Generate" taggedAs (IT) in {
    val workItems = JiraData
      .loadAsWorkItems(Config.props.jira.projects(projectKey).estimateMapping.get)
      .apply(JiraData.location(projectKey))

    workItems
      .filter(i => Seq("Story", "Bug").contains(i.`type`))
      .map(i => (
        i.statusHistory,
        CfdReporter.normalizeFlow(referenceFlow, entryState, finalState, stateMapping, i.statusHistory),
        i))
      .filter(_._2.isEmpty)
      .filterNot(_._1.filter(s => s.name == "Done").isEmpty)
      .take(100)
      .foreach(i => println(i._3.`type` + " " + i._3.name + ": " + i._1.sortBy(_.created.toInstant.toEpochMilli).map(_.name)))


    //Await.result(ReportsGenerator.namespaceActivityRank(ChronoUnit.WEEKS, 10), 20 minutes)
    //Await.result(ReportsGenerator.reposActivityRank(ChronoUnit.WEEKS, 10), 20 minutes)
    //Await.result(ReportsGenerator.committersActivityRank(ChronoUnit.WEEKS, 10), 20 minutes)
    //Await.result(ReportsGenerator.commitsStats(ChronoUnit.DAYS), 20 minutes)
    //    Await.result(ReportsGenerator.mergeRequests(ZonedDateTime.now().minusMonths(months)), 20 minutes)
    //    Await.result(ReportsGenerator.mergeRequestsComments("develop", ChronoUnit.WEEKS), 5 minutes)
    //    Await.result(ReportsGenerator.mergeRequestsDuration("develop", ChronoUnit.WEEKS), 5 minutes)
    //    Await.result(ReportsGenerator.mergeRequestsAuthorsRatio("develop", ChronoUnit.WEEKS), 5 minutes)
    //    Await.result(ReportsGenerator.commitsStatsByProjectRole(ChronoUnit.WEEKS), 5 minutes)

    //    val projects = GitlabClient.getProjects()
    //      .map(Serialization.write(_))
    //
    //    projects.onComplete(_.foreach(FileIO.saveTextContent(s"${Config.dataLocation}/gitlab-projects.json", _: String)))
    //    Await.result(projects, 10 minutes)
    //
    //    Await.result(ReportsGenerator.commitsStatsByProjectRole(ChronoUnit.WEEKS), 5 minutes)
    //    Await.result(ReportsGenerator.commitsStatsByProjectCategory(ChronoUnit.WEEKS), 5 minutes)
    //    Await.result(ReportsGenerator.mergeRequestsByProjectRole("develop", ChronoUnit.WEEKS), 5 minutes)
    //    Await.result(ReportsGenerator.mergeRequestsByProjectCategory("develop", ChronoUnit.WEEKS), 5 minutes)

    //    Await.result(ReportsGenerator.commits(ZonedDateTime.now().minusMonths(months)), 20 minutes)
    //    Await.result(ReportsGenerator.committersLifeSpanStats(ChronoUnit.MONTHS), 5 minutes)

    //    Await.result(GitlabInflux.loadMergeRequests().map(GitlabReporter.mergeRequestsCorrelation), 5 minutes)
    //        Await.result(ReportsGenerator.mergeRequests(ZonedDateTime.now().minusMonths(6)), 20 minutes)
    //    Await.result(ReportsGenerator.mergeRequestsByState("develop", ChronoUnit.WEEKS), 5 minutes)
    //    Await.result(ReportsGenerator.mergeRequestsByAuthor("develop", ChronoUnit.WEEKS), 5 minutes)
  }

}
