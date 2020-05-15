package net.oda

import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization

case class JiraProject(
                        estimateMapping: Map[String, Double],
                        referenceFlow: Map[String, Int],
                        entryState: String,
                        finalState: String,
                        stateMapping: Map[String, String],
                        prios: Seq[String]
                      )

case class JiraProps(
                      apiUrl: String,
                      user: String,
                      apiKey: String,
                      projects: Map[String, JiraProject]
                    )

case class DataProps(location: String = "./")

case class ReportsProps(location: String = "./")

case class HttpProps(port: Int)

case class InluxdbProps(host: String, port: Int, db: String)

case class GitlabProps(apiUrl: String, token: String)

case class ConfigProps(
                        jira: JiraProps,
                        gitlab: GitlabProps,
                        data: DataProps,
                        reports: ReportsProps,
                        http: HttpProps,
                        influxdb: InluxdbProps,
                        emailMapping: Map[String, String])

object Config {
  private implicit val formats = DefaultFormats
  val props = Serialization.read[ConfigProps](FileIO.loadTextContent("config.json"))
  val dataLocation = props.data.location
  val reportsLocation = props.reports.location

  def mapEmail(email: String) = props.emailMapping.get(email.toLowerCase).getOrElse(email.toLowerCase)
}
