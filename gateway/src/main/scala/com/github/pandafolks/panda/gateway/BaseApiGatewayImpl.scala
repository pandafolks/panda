package com.github.pandafolks.panda.gateway

import com.github.pandafolks.panda.loadbalancer.LoadBalancer
import com.github.pandafolks.panda.routes.{Group, TreesService}
import com.github.pandafolks.panda.utils.http.Responses
import monix.eval.Task
import org.http4s.Uri.Path
import org.http4s.{Request, Response}
import org.slf4j.LoggerFactory

final class BaseApiGatewayImpl(
    private val loadBalancer: LoadBalancer,
    private val treesService: TreesService
) extends ApiGateway {
  private val logger = LoggerFactory.getLogger(getClass.getName)

  override def ask(request: Request[Task], requestedPath: Path): Task[Response[Task]] =
    treesService
      .findStandaloneRoute(requestedPath, request.method)
      .flatMap {
        case None =>
          Task
            .now(s"${request.pathInfo} was not recognized as a supported path")
            .tapEval(message => Task.eval(logger.info(message)))
            .flatMap(message => Responses.notFoundWithInfo(message))
        case Some((routeInfo, _)) if routeInfo.mappingContent.left.isEmpty =>
          Task
            .now(
              s"${request.pathInfo} is a composition route, ${this.getClass.getName} does not support composition routes"
            )
            .tapEval(message => Task.eval(logger.info(message)))
            .flatMap(message => Responses.badRequestWithInfo(message))
        case Some((routeInfo, _)) => // Inside the BaseApiGateway, the wildcards' contents are not used.
          Task
            .now(routeInfo.mappingContent.left)
            .map(_.map(Group(_)))
            .flatMap {
              case Some(relatedGroup) =>
                treesService.findPrefix(relatedGroup).flatMap { prefix =>
                  loadBalancer.route(
                    request = request,
                    requestedPath = prefix.addSegments(requestedPath.segments),
                    group = relatedGroup
                  )
                }
              case None =>
                Task
                  .now(s"There is no mapping for the route ${request.pathInfo}")
                  .tapEval(message => Task.eval(logger.info(message)))
                  .flatMap(message => Responses.notFoundWithInfo(message))
            }
      }
}
