package net.oda.json

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import org.json4s.CustomSerializer
import org.json4s.JsonAST.JString

case object ZondedDateTimeSerializer extends CustomSerializer[ZonedDateTime](_ => ( {
  case JString(s) => ZonedDateTime.parse(s, DateTimeFormatter.ISO_ZONED_DATE_TIME)
}, {
  case ts: ZonedDateTime => JString(ts.format(DateTimeFormatter.ISO_ZONED_DATE_TIME))
}))