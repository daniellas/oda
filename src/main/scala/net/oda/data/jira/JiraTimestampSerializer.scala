package net.oda.data.jira

import org.json4s.CustomSerializer
import java.time.ZonedDateTime
import org.json4s.JsonAST.JString
import java.time.format.DateTimeFormatter
import scala.annotation.implicitNotFound
import scala.reflect.ManifestFactory.classType

case object JiraTimestampSerializer extends CustomSerializer[ZonedDateTime](
	format => (
		{
			case JString(s) => ZonedDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxx"))
		}, {
			case ts: ZonedDateTime => JString(ts.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxx")))
		}))