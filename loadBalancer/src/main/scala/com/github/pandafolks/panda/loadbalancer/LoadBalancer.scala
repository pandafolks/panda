package com.github.pandafolks.panda.loadbalancer

import com.github.pandafolks.panda.participant.Participant
import com.github.pandafolks.panda.routes.Group
import com.github.pandafolks.panda.utils.http.Responses
import monix.eval.Task
import org.http4s.{Header, Request, Response}
import org.http4s.Uri.{Authority, Path, RegName}
import org.slf4j.Logger
import org.typelevel.ci.CIString

trait LoadBalancer {
  /**
   * Routes request to one of the participants belonging to a related group.
   * The participant choice depends on the type of the implemented load balancer.
   *
   * @param request       Received request that needs to be forwarded
   * @param requestedPath Path that will be the request forwarded to
   * @param group         The group from which the participant should be selected
   * @return Reply received from the end server
   */
  def route(request: Request[Task], requestedPath: Path, group: Group): Task[Response[Task]]
}

object LoadBalancer {
  private final val HOST_NAME: String = "Host"

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
      .withHeaders(request.headers.put(
        Header.Raw(CIString(HOST_NAME), participant.host + ":" + participant.port.toString) // https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.23
      ))

  def notReachedAnyInstanceLog(requestedPath: Path, group: Group, logger: Logger): Task[Response[Task]] =
    Task.now(s"[path: ${requestedPath.renderString}]: Could not reach any of the instances belonging to the group \"${group.name}\"")
      .tapEval(message => Task.eval(logger.debug(message)))
      .flatMap(massage => Responses.serviceUnavailableWithInfo(massage))

  def noAvailableInstanceLog(requestedPath: Path, group: Group, logger: Logger): Task[Response[Task]] =
    Task.now(s"[path: ${requestedPath.renderString}]: There is no available instance for the related group \"${group.name}\"")
      .tapEval(message => Task.eval(logger.debug(message)))
      .flatMap(massage => Responses.notFoundWithInfo(massage))
}
