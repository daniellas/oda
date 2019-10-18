package net.oda

object FileCache {
  def usingCache(cacheLocation: String, identifiers: Any*): (() => String) => String = {
    data => {
      val cachePath = cacheLocation + "/" + Encoding.encodeFilePath(identifiers)
      val res = FileIO.tryLoadTextContent(cachePath).getOrElse(data.apply())
      FileIO.saveTextContent(cachePath, res)
      res
    }
  }

}
