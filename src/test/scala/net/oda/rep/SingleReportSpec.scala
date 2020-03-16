package net.oda.rep

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import com.typesafe.scalalogging.Logger
import net.oda.IT
import org.scalatest.FreeSpec

import scala.concurrent.Await
import scala.concurrent.duration._

class SingleReportsSpec extends FreeSpec {
  val log = Logger(classOf[SingleReportsSpec])

  s"Generate" taggedAs (IT) in {
    //    Await.result(ReportsGenerator.commits(ZonedDateTime.now().minusYears(5)), 20 minutes)
    //Await.result(ReportsGenerator.namespaceActivityRank(ChronoUnit.WEEKS, 10), 20 minutes)
    //Await.result(ReportsGenerator.reposActivityRank(ChronoUnit.WEEKS, 10), 20 minutes)
    //Await.result(ReportsGenerator.committersActivityRank(ChronoUnit.WEEKS, 10), 20 minutes)
    //Await.result(ReportsGenerator.commitsStats(ChronoUnit.DAYS), 20 minutes)

    Await.result(ReportsGenerator.mergeRequests(ZonedDateTime.now().minusYears(5)), 20 minutes)
  }

}
