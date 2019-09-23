package net.oda.json

import org.json4s.Formats
import org.json4s.jackson.Serialization

object JsonSer {
	def writeAsString[A <: AnyRef](implicit formats: Formats, a: A): String = {
		Serialization.write(a)
	}
}