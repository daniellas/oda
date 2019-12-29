package net.oda.rest.client

case class Response[A](
                        statusCode: Int,
                        statusLine: String,
                        headers: Map[String, Seq[String]],
                        body: Option[A]) {
  def map[B](mapper: A => B): Response[B] = Response(statusCode, statusLine, headers, body.map(mapper))
}
