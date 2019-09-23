package net.oda

import java.util.Properties

object Config {
  private val props = IO.newInputStream
    .andThen(is => {
      val p = new Properties()

      p.load(is)
      p
    })
    .apply("config.properties")

  val getProp = (name: String) => Option(name)
    .map(props.getProperty)
}
