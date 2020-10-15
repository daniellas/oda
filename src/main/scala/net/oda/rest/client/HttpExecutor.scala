package net.oda.rest.client

import scala.concurrent.Future

/**
 *
 * @tparam A
 */
trait HttpExecutor[A] {
  /**
   * Execute HTTP request
   *
   * @param url     absolute URL of request
   * @param headers to put in request
   * @param method  HTTP to use for request
   * @param body    to send
   * @return response future
   */
  def execute(url: String, headers: Map[String, Seq[String]], method: String, body: Option[String]): Future[Response[A]]
}
