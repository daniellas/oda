package net.oda

import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization

case class JiraProps(
                      apiUrl: String,
                      user: String,
                      apiKey: String,
                      estimateMapping: Map[String, Double],
                      referenceFlow: Map[String, Int],
                      entryState: String,
                      finalState: String,
                      stateMapping: Map[String, String]
                    )

case class DataProps(location: String = "./")

case class ReportsProps(location: String = "./")

case class HttpProps(port: Int)

case class ConfigProps(
                        jira: JiraProps,
                        data: DataProps,
                        reports: ReportsProps,
                        http: HttpProps)

object Config {
  private implicit val formats = DefaultFormats
  val props = Serialization.read[ConfigProps](FileIO.loadTextContent("config.json"))

  val reportsLocation = props.data.location
  val dataLocation = props.reports.location
}
