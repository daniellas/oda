package net.oda

import java.sql.Timestamp
import java.time.temporal.ChronoUnit
import java.time._

object Time {

  implicit def toTimestamp(dt: ZonedDateTime) = Timestamp.valueOf(dt.toLocalDateTime)

  implicit def toTimestamp(dt: LocalDate) = Timestamp.valueOf(dt.atStartOfDay())

  implicit def toTimestamp(dt: LocalDateTime) = Timestamp.valueOf(dt)

  implicit def toZonedDateTime(ts: Timestamp) = ZonedDateTime.from(ts.toInstant.atZone(ZoneId.systemDefault()))

  implicit def toLocalDate(ts: Timestamp) = LocalDate.from(ts.toInstant.atZone(ZoneId.systemDefault()))

  implicit def toLocalDate(epochMillis: Long) = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate

  implicit def parseLocalDate(date: String) = LocalDate.parse(date)

  implicit def weekStart(dt: ZonedDateTime) = dt.truncatedTo(ChronoUnit.DAYS).`with`(DayOfWeek.MONDAY)

  implicit def day(dt: ZonedDateTime) = dt.truncatedTo(ChronoUnit.DAYS)

  val weeksBetween = (start: LocalDate, end: LocalDate) => ChronoUnit.WEEKS.between(start, end)

  val daysBetween = (start: LocalDate, end: LocalDate) => ChronoUnit.DAYS.between(start, end)

  val weeksRange = (start: LocalDate, end: LocalDate) => (0L to weeksBetween(start, end)).toList.map(start.plusWeeks)

  val daysRange = (start: LocalDate, end: LocalDate) => (0L to daysBetween(start, end)).toList.map(start.plusDays)

  val interval: (ChronoUnit, Timestamp) => Timestamp = (interval: ChronoUnit, ts: Timestamp) => interval match {
    case ChronoUnit.DAYS => day(ts)
    case ChronoUnit.WEEKS => weekStart(ts)
    case _ => ts
  }

  val range: (ChronoUnit, Timestamp, Timestamp) => Seq[Timestamp] = (interval: ChronoUnit, start: Timestamp, end: Timestamp) =>
    (0L to interval.between(start.toLocalDate, end.toLocalDate)).map(start.toLocalDate.plus(_, interval)).map(toTimestamp)
}
