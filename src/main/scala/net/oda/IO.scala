package net.oda

import java.nio.file.{Files, Paths, StandardOpenOption}

object IO {
  def saveTextContent(path: String, content: String) = {
    Files.write(Paths.get(path), content.getBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
  }

  val loadTextContent = (path: String) => new String(Files.readAllBytes(Paths.get(path)))

  val newInputStream = (path: String) => Files.newInputStream(Paths.get(path))
}