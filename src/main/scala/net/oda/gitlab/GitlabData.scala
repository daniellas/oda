package net.oda.gitlab

import net.oda.{Config, FileIO}
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization

object GitlabData {
  implicit val formats = DefaultFormats

  def loadProjects() = FileIO
    .loadTextContent
    .andThen(Serialization.read[Seq[Project]])
    .apply(s"${Config.dataLocation}/gitlab-projects.json")
}
