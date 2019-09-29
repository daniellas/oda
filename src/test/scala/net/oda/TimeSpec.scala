package net.oda

import java.time.ZoneId.systemDefault
import java.time.{LocalDate, ZonedDateTime}

import net.oda.Time._
import org.scalatest.{FreeSpec, Matchers}

class TimeSpec extends FreeSpec with Matchers {
  "Time" - {
    "should calculate week start" in {
      weekStart(ZonedDateTime.of(2019, 9, 29, 0, 0, 0, 0, systemDefault())) should equal(ZonedDateTime.of(2019, 9, 23, 0, 0, 0, 0, systemDefault()))
    }

    "should calculate weeks between" in {
      weeksBetween("2019-09-23", "2019-09-30") should equal(1)
    }

    "should generate weeks range" in {
      weeksRange("2019-09-02", "2019-09-30") should (
        contain allOf (
          LocalDate.of(2019, 9, 2),
          LocalDate.of(2019, 9, 9),
          LocalDate.of(2019, 9, 16),
          LocalDate.of(2019, 9, 23),
          LocalDate.of(2019, 9, 30))
        )
    }
  }
}
