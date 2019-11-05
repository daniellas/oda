package net.oda.it

import java.time.LocalDate
import java.time.temporal.ChronoUnit

import net.oda.data.jira.{Issue, JiraTimestampSerializer, Mappers}
import net.oda.rep.cfd.CFDReporter
import net.oda.{Config, FileIO, IT}
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization
import org.scalatest.FreeSpec
import org.slf4j.LoggerFactory
import org.apache.spark.sql.functions._

import scala.collection.SortedMap

class CrypCFDSpec extends FreeSpec {
  val log = LoggerFactory.getLogger("oda")
  implicit val formats = DefaultFormats + JiraTimestampSerializer
  val projectKey = "CRYP"
  val dataLocation = Config.dataLocation
  val entryState = "To Do"
  val finalState = "Done"
  val stateMapping = Map("Invalid" -> "Done")
  val referenceFlow = Map(
    "Backlog" -> -1,
    "Upcoming" -> 0,
    "To Do" -> 1,
    "In Progress" -> 2,
    "In Review" -> 3,
    "Ready to test" -> 4,
    "In testing" -> 5,
    "Done" -> 6
  )

  s"Generate ${projectKey} critical bugs CFD" taggedAs (IT) in {
    generate(entryState, finalState, Array("Bug").contains, Array("Critical").contains, None, "Bug-Critical", ChronoUnit.DAYS)
  }

  s"Generate ${projectKey} CFD" taggedAs (IT) in {
    generate(entryState, finalState, Array("Story", "Bug").contains, _ => true, None, "Story_Bug-All", ChronoUnit.WEEKS)
  }

  s"Generate ${projectKey} S items CFD" taggedAs (IT) in {
    generate("In Progress", "Done", Array("Story").contains, _ => true, Some("S".equals), "Story-All-S", ChronoUnit.DAYS)
  }

  s"Generate ${projectKey} M items CFD" taggedAs (IT) in {
    generate("In Progress", "Done", Array("Story").contains, _ => true, Some("M".equals), "Story_All-M", ChronoUnit.DAYS)
  }

  s"Generate ${projectKey} L items CFD" taggedAs (IT) in {
    generate("In Progress", "Done", Array("Story").contains, _ => true, Some("L".equals), "Story_All-L", ChronoUnit.DAYS)
  }

  s"Generate ${projectKey} XL items CFD" taggedAs (IT) in {
    generate("In Progress", "Done", Array("Story").contains, _ => true, Some("XL".equals), "Story_All-XL", ChronoUnit.DAYS)
  }

  def generate(
                entryState: String,
                finalState: String,
                types: String => Boolean,
                priorities: String => Boolean,
                size: Option[String => Boolean],
                suffix: String,
                interval: ChronoUnit
              ): Unit = {
    log.info("Generating {} CFD", projectKey + "/" + suffix)

    val report = FileIO.loadTextContent
      .andThen(Serialization.read[List[Issue]])
      .andThen(_.map(Mappers.jiraIssueToWorkItem(_, _ => Some(0))))
      .andThen(CFDReporter.generate(
        projectKey,
        LocalDate.MIN,
        types,
        priorities,
        referenceFlow,
        entryState,
        finalState,
        stateMapping,
        interval,
        count(lit(1)),
        _))
      .apply(s"${dataLocation}/jira-issues-${projectKey}.json")

    report
      .write
      .format("csv")
      .mode("overwrite")
      .option("header", value = true)
      .save(Config.reportsLocation + s"/cfd-${projectKey}-${suffix}")

    log.info("{} CFD ready", projectKey + "/" + suffix)
  }
}
