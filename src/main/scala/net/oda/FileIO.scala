package net.oda

import java.nio.file.{Files, Paths, StandardOpenOption}

import scala.util.{Failure, Success, Try}

object FileIO {
  def saveTextContent(path: String, content: String) = {
    Files.write(Paths.get(path), content.getBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
  }

  val loadTextContent = (path: String) => new String(Files.readAllBytes(Paths.get(path)))

  val tryLoadTextContent: String => Try[String] = (path: String) => {
    try {
      Success(loadTextContent(path))
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  val newInputStream = (path: String) => Files.newInputStream(Paths.get(path))
}