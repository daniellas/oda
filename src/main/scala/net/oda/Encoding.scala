package net.oda

import java.security.MessageDigest
import java.util.Base64

object Encoding {
  private val md5 = MessageDigest.getInstance("MD5")

  val encodeFilePath = (vals: Seq[Any]) =>
    Base64
      .getEncoder
      .encodeToString(md5.digest(vals.map(_.toString).reduce(_ + _).getBytes))
      .replace("/", "")
      .replace("=", "")
}
