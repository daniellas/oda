package net.oda.it

import java.time.temporal.ChronoUnit

import com.typesafe.scalalogging.Logger
import net.oda.jira.{JiraData, JiraReporter}
import net.oda.rep.ReportsGenerator
import net.oda.{Config, IT}
import org.scalatest.FreeSpec

import scala.concurrent.Await
import scala.concurrent.duration._

class SingleReportsSpec extends FreeSpec {
  val log = Logger(classOf[SingleReportsSpec])

  s"Generate" taggedAs (IT) in {
    val devStateFilter = (state: String) => Seq("In Progress", "In Review", "Ready to test", "In testing").contains(state)

//    JiraData
//      .loadAsWorkItems
//      .andThen(JiraReporter.teamProductivityFactor(_, devStateFilter, ChronoUnit.WEEKS))
//      .apply(JiraData.location("CRYP"))

    Await.result(ReportsGenerator.workItemsChangelog("CRYP", ChronoUnit.DAYS), 100 second)

  }

}
