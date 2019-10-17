package net.oda

import java.util.Properties

object Config {
  private val props = FileIO.newInputStream
    .andThen(is => {
      val p = new Properties()

      p.load(is)
      p
    })
    .apply("config.properties")

  val getProp = (name: String) => Option(name)
    .flatMap((n: String) => Option(props.get(n)))
    .map(_.toString)

}
