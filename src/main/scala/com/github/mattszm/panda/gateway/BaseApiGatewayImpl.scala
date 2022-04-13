package com.github.mattszm.panda.gateway

import com.github.mattszm.panda.participants.Participant
import com.github.mattszm.panda.routes.Group
import com.github.mattszm.panda.participants.ParticipantsCacheImpl
import com.github.mattszm.panda.routes.RoutesTree
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
      Participant("127.0.0.1", 4000, Group("planes"))
    )
  )

  override def ask(request: Request[Task], requestedPath: Path): Task[Response[Task]] = {
    routesTree.specifyGroup(requestedPath) match {
      case None =>
        logger.debug("\"" + requestedPath.renderString + "\"" + " was not recognized as a supported path")
        Response.notFoundFor(request)
      case Some(groupInfo) =>
        Task.eval(participantsCache.getParticipantsAssociatedWithGroup(groupInfo.group))
          .map(_.headOption) // this and the previous will be replaced by loadBalancer
          .flatMap(optionalParticipant => optionalParticipant
            .fold(
              {
                logger.info("There is no available instance for the requested path: \"" + requestedPath.renderString + "\"")
                Response.notFoundFor(request)
              }
            )(chosenParticipant =>
              client.run(
                request
                  .withUri(request.uri.copy(
                    authority = Some(Authority(
                      host = RegName(chosenParticipant.host),
                      port = Some(chosenParticipant.port)
                    )),
                    path = requestedPath
                  ))
                  .withHeaders(request.headers.put(Header.Raw(CIString("host"), chosenParticipant.host)))
              ).use(Task.eval(_))
            )
          ).onErrorRecoverWith { case err: Throwable =>
          logger.info("[path: \"" + requestedPath.renderString + "\"] " + err.getMessage)
          Response.notFoundFor(request)
        } // here probably recovery and trying to hit another server
    }
  }
}
