package net.oda.rep

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import com.typesafe.scalalogging.Logger
import net.oda.IT
import net.oda.gitlab.{GitlabInflux, GitlabReporter}
import org.scalatest.FreeSpec

import scala.concurrent.Await
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global

class SingleReportsSpec extends FreeSpec {
  val log = Logger(classOf[SingleReportsSpec])
  val months = 1;

  s"Generate" taggedAs (IT) in {
    //    Await.result(ReportsGenerator.commits(ZonedDateTime.now().minusMonths(months)), 20 minutes)
    //Await.result(ReportsGenerator.namespaceActivityRank(ChronoUnit.WEEKS, 10), 20 minutes)
    //Await.result(ReportsGenerator.reposActivityRank(ChronoUnit.WEEKS, 10), 20 minutes)
    //Await.result(ReportsGenerator.committersActivityRank(ChronoUnit.WEEKS, 10), 20 minutes)
    //Await.result(ReportsGenerator.commitsStats(ChronoUnit.DAYS), 20 minutes)
    //    Await.result(ReportsGenerator.mergeRequests(ZonedDateTime.now().minusMonths(months)), 20 minutes)

    //Await.result(GitlabInflux.loadMergeRequests().map(GitlabReporter.mergeRequestsCfd(_, ChronoUnit.WEEKS)), 5 minutes)

    Await.result(ReportsGenerator.mergeRequestsStats("develop", ChronoUnit.WEEKS), 5 minutes)
    Await.result(ReportsGenerator.mergeRequestsMovingAverage("develop", ChronoUnit.WEEKS), 5 minutes)
  }

}
