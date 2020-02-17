package net.oda.rep

import java.time.ZonedDateTime

import com.typesafe.scalalogging.Logger
import net.oda.IT
import org.scalatest.FreeSpec

import scala.concurrent.Await
import scala.concurrent.duration._

class SingleReportsSpec extends FreeSpec {
  val log = Logger(classOf[SingleReportsSpec])

  s"Generate" taggedAs (IT) in {
    Await.result(ReportsGenerator.commits(ZonedDateTime.now().minusYears(5)), 20 minutes)
  }

}
