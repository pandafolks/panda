package com.github.pandafolks.panda.utils.http

import org.http4s.{Header, Headers, Request}
import org.typelevel.ci.CIString

object RequestUtils {

  final val XForwardedForHeader: CIString = CIString("X-Forwarded-For")
  final val HostNameHeader: CIString = CIString("Host")

  /** Gets the real host from the request. Picks value of the X-Forwarded-For header if exists, host value otherwise. As X-Forwarded-For
    * header can contain multiple values, the leftmost IP address is picked - the IP address of the originating client.
    *
    * @param request
    *   source to get info from
    * @return
    *   value based on X-Forwarded-For header or Host. None if none exists.
    */
  def getRealHost[F[_]](request: Request[F]): Option[String] =
    request.headers
      .get(XForwardedForHeader)
      .map(_.head.value)
      .flatMap(_.split(""",""").map(_.trim).find(_.nonEmpty))
      .orElse(request.remote.map(_.host.toUriString))

  /** Returns headers with a single entry "X-Forwarded-For" created based on the old value and the host.
    *
    * @param headers
    *   origin headers
    * @return
    *   headers with one (X-Forwarded-For header) or zero elements
    */
  def withUpdatedXForwardedForHeader(headers: Headers, host: Option[String]): Headers =
    host
      .map(someHost =>
        headers
          .get(XForwardedForHeader)
          .map(_.head)
          .filter(_.value.trim.nonEmpty)
          .map(xForwardedForHeader => Header.Raw(XForwardedForHeader, xForwardedForHeader.value + s", $someHost"))
          .getOrElse(Header.Raw(XForwardedForHeader, someHost))
      )
      .map(Headers(_))
      .getOrElse(Headers())

  /** Returns headers with a single entry "Host" created based on the host and port values.
    *
    * @param host
    * @param port
    * @return
    *   headers with one (Host) element
    */
  def withHostHeader(host: String, port: Int): Headers =
    Headers(
      Header.Raw(
        HostNameHeader,
        host + ":" + port.toString
      )
    ) // https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.23
}
