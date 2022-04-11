package com.mattszm.panda.gateway

import com.mattszm.panda.participants.{Participant, ParticipantsCacheImpl}
import com.mattszm.panda.routes.{Group, RoutesTree}
import monix.eval.Task
import org.http4s.Uri.{Authority, Path, RegName}
import org.http4s.client.Client
import org.http4s.{Header, Request, Response}
import org.slf4j.LoggerFactory
import org.typelevel.ci.CIString

final class BaseApiGatewayImpl(
                                private val client: Client[Task],
                                private val routesTree: RoutesTree,
                    ) extends ApiGateway {
  private val logger = LoggerFactory.getLogger(getClass.getName)

  // temporary solution - participants will be registered remotely
  private val participantsCache = new ParticipantsCacheImpl(
    List(Participant("127.0.0.1", 3000, Group("cars")),
      Participant("localhost", 3001, Group("cars")),
      Participant("127.0.0.1", 4000, Group("planes")))
  )
  println(participantsCache.cacheByGroup)
  println(routesTree.getHead)
  logger.info("Gateway Tree initialized")

  override def ask(request: Request[Task], requestedPath: Path): Task[Response[Task]] = {
    val newHost = "localhost" // hardcoded
    val newPort = 3000 // hardcoded

    val proxiedReq =
      request
        .withUri(request.uri.copy(
          authority = Some(Authority(host = RegName(newHost), port = Some(newPort))),
          path = requestedPath
        ))
        .withHeaders(request.headers.put(Header.Raw(CIString("host"), newHost)))

    client.run(proxiedReq).use(Task.eval(_)).onErrorRecoverWith { case _: Throwable => Task.eval(Response.notFound) }
  }
}
