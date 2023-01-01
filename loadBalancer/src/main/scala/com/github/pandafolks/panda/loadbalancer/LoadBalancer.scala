package com.github.pandafolks.panda.loadbalancer

import com.github.pandafolks.panda.participant.Participant
import com.github.pandafolks.panda.routes.Group
import com.github.pandafolks.panda.utils.http.{RequestUtils, Responses}
import monix.eval.Task
import org.http4s.Uri.{Authority, Path, RegName}
import org.http4s.{Request, Response}
import org.slf4j.Logger

trait LoadBalancer {

  /** Routes request to one of the participants belonging to a related group. The participant choice depends on the type of the implemented
    * load balancer.
    *
    * @param request
    *   Received request that needs to be forwarded
    * @param requestedPath
    *   Path that will be the request forwarded to
    * @param group
    *   The group from which the participant should be selected
    * @return
    *   Reply received from the end server
    */
  def route(request: Request[Task], requestedPath: Path, group: Group): Task[Response[Task]]
}

object LoadBalancer {

  def fillRequestWithParticipant(request: Request[Task], participant: Participant, requestedPath: Path): Request[Task] =
    request
      .withUri(
        request.uri.copy(
          authority = Some(
            Authority(
              host = RegName(participant.host),
              port = Some(participant.port)
            )
          ),
          path = requestedPath
        )
      )
      .withHeaders(
        request.headers
          ++ RequestUtils.withHostHeader(participant.host, participant.port)
          ++ RequestUtils.withUpdatedXForwardedForHeader(
            request.headers,
            request.remote.map(_.host.toUriString)
          )
      )

  def notReachedAnyInstanceLog(requestedPath: Path, group: Group, logger: Logger): Task[Response[Task]] =
    Task
      .now(
        s"[path: ${requestedPath.renderString}]: Could not reach any of the instances belonging to the group \"${group.name}\""
      )
      .tapEval(message => Task.eval(logger.info(message)))
      .flatMap(massage => Responses.serviceUnavailableWithInfo(massage))

  def noAvailableInstanceLog(requestedPath: Path, group: Group, logger: Logger): Task[Response[Task]] =
    Task
      .now(
        s"[path: ${requestedPath.renderString}]: There is no available instance for the related group \"${group.name}\""
      )
      .tapEval(message => Task.eval(logger.info(message)))
      .flatMap(massage => Responses.notFoundWithInfo(massage))
}
