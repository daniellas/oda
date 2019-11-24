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
    val devStateFilter = (state: String) => !Seq("Backlog", "Upcoming").contains(state)

    JiraData
      .loadAsWorkItems
      .andThen(JiraReporter.teamProductivityFactor(_, devStateFilter, ChronoUnit.WEEKS))
      .apply(JiraData.location("CRYP"))
  }

}
