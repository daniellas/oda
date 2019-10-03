package net.oda.json

import java.io.FileOutputStream
import java.nio.file.Paths

import org.json4s.Formats
import org.json4s.jackson.Serialization

object JsonSer {
  def writeAsString[A <: AnyRef](implicit formats: Formats, a: A): String = {
    Serialization.write(a)
  }

  def writeToFile[A <: AnyRef](implicit formats: Formats, path: String, a: A): Unit = {
    Serialization.write(a, new FileOutputStream(Paths.get(path).toFile))
  }
}