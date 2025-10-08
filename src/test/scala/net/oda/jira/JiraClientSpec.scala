package net.oda.jira

import com.typesafe.scalalogging.Logger
import net.oda.{Config, IT}
import org.scalatest.FreeSpec

import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class JiraClientSpec extends FreeSpec {
  val log = Logger(classOf[JiraClientSpec])

  "Get issues" taggedAs (IT) in {
    Await.result(JiraClient.searchIssues("QT"), Duration(10, TimeUnit.MINUTES))
  }
}
