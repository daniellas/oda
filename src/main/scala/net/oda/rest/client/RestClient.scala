package net.oda.rest.client

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import scala.concurrent.Future

trait Executor[A] extends Function0[Future[Response[A]]] {
  def execute(): Future[Response[A]] = apply()
}

trait RequestHeaders[A, B] extends Function1[Map[String, Seq[String]], Executor[B]] {
  def execute() = apply(Map.empty).execute()
}

trait Body[A, B] extends Function1[Option[String], RequestHeaders[A, B]] {
  def body(body: String) = apply(Some(body))

  def noBody() = apply(None)

  def execute() = noBody().execute()
}

trait Method[A, B] extends Function1[String, Body[A, B]] {
  def method(method: String) = apply(method)

  def get() = apply(RestClient.METHOD_GET).noBody()
}

trait Resource[A, B] extends Function1[String, Method[A, B]] {
  def resource(path: String) = apply(path)

  def resource(parser: (String, Any *) => String, path: String, params: Any*) = apply(parser.apply(path, params))

  def resource(path: String, params: Any*): Method[A, B] = resource {
    path.format(params.map(p => URLEncoder.encode(p.toString, StandardCharsets.UTF_8.name)): _*)
  }
}

trait DefaultHeaders[A, B] extends Function2[Map[String, Seq[String]], () => Map[String, Seq[String]], Resource[A, B]] {
  def defaultHeaders(defaultHeaders: Map[String, Seq[String]], dynamicHeaders: () => Map[String, Seq[String]]) =
    apply(defaultHeaders, dynamicHeaders)

  def defaultHeaders(defaultHeaders: Map[String, Seq[String]]) = apply(defaultHeaders, () => Map.empty)

  def defaultHeaders(dynamicHeaders: () => Map[String, Seq[String]]) = apply(Map.empty, dynamicHeaders)

  def noDefaultHeaders() = apply(Map.empty, () => Map.empty)

  def resource(path: String) = noDefaultHeaders().resource(path)
}

trait Service[A, B] extends Function1[String, DefaultHeaders[A, B]] {
  def service(url: String) = apply(url)
}

object RestClient {
  val METHOD_GET = "GET"
  val METHOD_PUT = "PUT"
  val METHOD_POST = "POST"
  val METHOD_DELETE = "DELETE"
  val METHOD_PATCH = "PATCH"
  val METHOD_OPTIONS = "OPTIONS"

  def using[A, B](httpExecutor: HttpExecutor[B], serviceResolver: String => String): Service[A, B] =
    service =>
      (defaultHeaders, dynamicHeaders) =>
        resource =>
          method =>
            body =>
              headers =>
                () => httpExecutor.execute(
                  serviceResolver.apply(service) + resource,
                  defaultHeaders ++ dynamicHeaders.apply ++ headers,
                  method,
                  body)

  def using[A, B](httpExecutor: HttpExecutor[B]): Service[A, B] = using(httpExecutor, identity)

}
