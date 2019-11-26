package net.oda.rep

import java.time.temporal.ChronoUnit

import com.typesafe.scalalogging.Logger
import net.oda.{Config, IT}
import org.scalatest.FreeSpec

import scala.concurrent.Await
import scala.concurrent.duration._

class SingleReportsSpec extends FreeSpec {
  val log = Logger(classOf[SingleReportsSpec])

  s"Generate" taggedAs (IT) in {
    val project = Config.props.jira.projects("CRYP")

    Await.result(
      ReportsGenerator
        .jiraCfd(
          "CRYP",
          project.entryState,
          project.finalState,
          project.stateMapping,
          project.referenceFlow,
          Seq("Story", "Bug").contains,
          _ => true,
          ChronoUnit.MONTHS,
          "All stories and bugs"
        ), 100 second)
  }

}
