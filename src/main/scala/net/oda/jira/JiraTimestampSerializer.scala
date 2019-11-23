package net.oda.jira

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import org.json4s.CustomSerializer
import org.json4s.JsonAST.JString

case object JiraTimestampSerializer extends CustomSerializer[ZonedDateTime](
	format => (
		{
			case JString(s) => ZonedDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxx"))
		}, {
			case ts: ZonedDateTime => JString(ts.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxx")))
		}))