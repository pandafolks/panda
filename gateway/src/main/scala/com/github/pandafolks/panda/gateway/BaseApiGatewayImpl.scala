package com.github.pandafolks.panda.gateway

import com.github.pandafolks.panda.loadbalancer.LoadBalancer
import com.github.pandafolks.panda.routes.{Group, TreesService}
import monix.eval.Task
import org.http4s.Uri.Path
import org.http4s.{Request, Response}
import org.slf4j.LoggerFactory

final class BaseApiGatewayImpl(
                                private val loadBalancer: LoadBalancer,
                                private val treesService: TreesService,
                              ) extends ApiGateway {
  private val logger = LoggerFactory.getLogger(getClass.getName)

  override def ask(request: Request[Task], requestedPath: Path): Task[Response[Task]] =
    treesService.findStandaloneRoute(requestedPath, request.method)
      .flatMap {
        case None =>
          logger.debug("\"" + requestedPath.renderString + "\"" + " was not recognized as a supported path.") // todo: this log should be saved inside the access logs.
          Response.notFoundFor(request)
        case Some((routeInfo, _)) if routeInfo.mappingContent.left.isEmpty =>
          logger.debug("\"" + requestedPath.renderString + "\"" + s" is a composition route, " +
            s"${this.getClass.getName} does not support composition routes.") // todo: this log should be saved inside the access logs.
          Response.notFoundFor(request)
        case Some((routeInfo, _)) =>
          Task.now(Group(routeInfo.mappingContent.left.get)).flatMap { relatedGroup =>
            treesService.findPrefix(relatedGroup).flatMap { prefix =>
              loadBalancer.route(
                request = request,
                requestedPath = prefix.addSegments(requestedPath.segments),
                group = relatedGroup
              )
            }
          }
      }
}
