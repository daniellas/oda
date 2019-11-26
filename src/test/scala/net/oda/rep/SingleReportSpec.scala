package net.oda.rep

import java.time.temporal.ChronoUnit

import com.typesafe.scalalogging.Logger
import net.oda.IT
import org.scalatest.FreeSpec

import scala.concurrent.Await
import scala.concurrent.duration._

class SingleReportsSpec extends FreeSpec {
  val log = Logger(classOf[SingleReportsSpec])

  s"Generate" taggedAs (IT) in {
    val devStateFilter = (state: String) => !Seq("Backlog", "Upcoming", "Done").contains(state)

    Await.result(ReportsGenerator.teamProductivityFactor("CRYP", devStateFilter, ChronoUnit.DAYS, 12D * 7), 100 second)
  }

}
