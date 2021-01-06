package net.oda.jira

import java.time.{LocalDate, ZonedDateTime}
import java.time.format.DateTimeFormatter

import org.json4s.CustomSerializer
import org.json4s.JsonAST.JString

case object JiraDateSerializer extends CustomSerializer[LocalDate](
  format => ( {
    case JString(s) => LocalDate.parse(s)
  }, {
    case ts: LocalDate => JString(ts.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
  }))