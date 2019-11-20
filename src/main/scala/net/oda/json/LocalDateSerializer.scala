package net.oda.json

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import org.json4s.CustomSerializer
import org.json4s.JsonAST.JString

case object LocalDateSerializer extends CustomSerializer[LocalDate](_ => ( {
  case JString(s) => LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
}, {
  case ts: LocalDate => JString(ts.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
}))