package net.oda.data.jira

import java.time.{ZoneId, ZonedDateTime}

import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization
import org.scalatest.{FreeSpec, Matchers}

case class Model(ts: ZonedDateTime) {

}

class JiraTimestampSerializerSpec extends FreeSpec with Matchers {
	"JIRA Timestamp serializer" - {
		"should parse timestamp string with zone offset" in {
			implicit val formats = DefaultFormats + JiraTimestampSerializer

			Serialization.read[Model]("{\"ts\":\"2019-08-05T10:19:50.000+0200\"}").ts should equal(ZonedDateTime.of(2019, 8, 5, 10, 19, 50, 0, ZoneId.of("+0200")))
		}
	}
}