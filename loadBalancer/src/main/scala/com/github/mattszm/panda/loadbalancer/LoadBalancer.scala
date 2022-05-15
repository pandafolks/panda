package com.github.mattszm.panda.loadbalancer

import com.github.mattszm.panda.participant.Participant
import com.github.mattszm.panda.routes.Group
import monix.eval.Task
import org.http4s.{Header, Request, Response}
import org.http4s.Uri.{Authority, Path, RegName}
import org.slf4j.Logger
import org.typelevel.ci.CIString

trait LoadBalancer {
  def route(request: Request[Task], requestedPath: Path, group: Group): Task[Response[Task]]
}

object LoadBalancer {
  private final val HOST_NAME: String = "host"

  def fillRequestWithParticipant(request: Request[Task], participant: Participant, requestedPath: Path): Request[Task] =
    request
      .withUri(
        request.uri.copy(
          authority = Some(Authority(
            host = RegName(participant.host),
            port = Some(participant.port)
          )),
          path = requestedPath
        )
      )
      .withHeaders(request.headers.put(Header.Raw(CIString(HOST_NAME), participant.host)))

  def notReachedAnyInstanceLog(requestedPath: Path, group: Group, logger: Logger): Unit =
    logger.info("[path: \"" + requestedPath.renderString + "\"]: " +
      "Could not reach any of the instances belonging to the group " + "\"" + group.name + "\"")

  def noAvailableInstanceLog(requestedPath: Path, logger: Logger): Unit =
    logger.info("There is no available instance for the requested path: \"" + requestedPath.renderString + "\"")
}
