package net.oda.jira

import net.oda.{FileIO, IT}
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization
import org.scalatest.FreeSpec

class JiraSerializationSpec extends FreeSpec {
  implicit val formats = DefaultFormats + JiraTimestampSerializer

  "Should read" taggedAs (IT) in {
    FileIO.loadTextContent.andThen(Serialization.read[JiraIssues]).apply("tmp.json")
  }

}
