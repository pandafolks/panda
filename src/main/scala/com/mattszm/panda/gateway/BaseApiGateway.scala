package com.mattszm.panda.gateway

import monix.eval.Task
import org.http4s.Uri.{Authority, Path, RegName}
import org.http4s.client.Client
import org.http4s.{Header, Request, Response}
import org.slf4j.LoggerFactory
import org.typelevel.ci.CIString
import ujson.Value

class BaseApiGateway(
                      private val gatewayConfiguration: Value.Value,
                      private val client: Client[Task],
                    ) extends ApiGateway {
  private val logger = LoggerFactory.getLogger(getClass.getName)

  logger.info("Endpoints tree initialized")
  // creating in memory structure here!

  override def getResponse(request: Request[Task], requestedPath: Path): Task[Response[Task]] = {
    val newHost = "localhost" // hardcoded
    val port = 3000 // hardcoded

    val proxiedReq =
      request
        .withUri(request.uri.copy(
          authority = Some(Authority(host = RegName(newHost), port = Some(port))),
          path = requestedPath
        ))
        .withHeaders(request.headers.put(Header.Raw(CIString("host"), newHost)))

    client.run(proxiedReq).use(Task.eval(_)).onErrorRecoverWith { case _: Throwable => Task.eval(Response.notFound) }
  }
}
