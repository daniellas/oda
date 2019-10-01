package net.oda

import java.sql.Timestamp
import java.time.temporal.ChronoUnit
import java.time._

object Time {

  implicit def toTimestamp(dt: ZonedDateTime) = Timestamp.valueOf(dt.toLocalDateTime)

  implicit def toTimestamp(dt: LocalDate) = Timestamp.valueOf(dt.atStartOfDay())

  implicit def toZonedDateTime(ts: Timestamp) = ZonedDateTime.from(ts.toInstant.atZone(ZoneId.systemDefault()))

  implicit def toLocalDate(ts: Timestamp) = LocalDate.from(ts.toInstant.atZone(ZoneId.systemDefault()))

  implicit def parseLocalDate(date: String) = LocalDate.parse(date)

  implicit def weekStart(dt: ZonedDateTime) = dt.truncatedTo(ChronoUnit.DAYS).`with`(DayOfWeek.MONDAY)

  val weeksBetween = (start: LocalDate, end: LocalDate) => ChronoUnit.WEEKS.between(start, end)

  val weeksRange = (start: LocalDate, end: LocalDate) => (0L to weeksBetween(start, end)).toList.map(start.plusWeeks)
}
