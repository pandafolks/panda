package com.github.pandafolks.panda.gateway

import com.github.pandafolks.panda.loadbalancer.LoadBalancer
import com.github.pandafolks.panda.routes.{Group, RoutesTreesHandler}
import monix.eval.Task
import org.http4s.Uri.Path
import org.http4s.{Request, Response}
import org.slf4j.LoggerFactory

final class BaseApiGatewayImpl(
                                private val loadBalancer: LoadBalancer,
                                private val routesTrees: RoutesTreesHandler,
                              ) extends ApiGateway {
  private val logger = LoggerFactory.getLogger(getClass.getName)

  override def ask(request: Request[Task], requestedPath: Path): Task[Response[Task]] = {
    Task.eval(routesTrees.get(request.method).get) // comeback here
      .map(_.specifyGroup(requestedPath))
      .map(_.map(_._1)) // <- todo mszmal: temporary
      .flatMap {
        case None =>
          logger.debug("\"" + requestedPath.renderString + "\"" + " was not recognized as a supported path")
          Response.notFoundFor(request)
        case Some(routeInfo) => loadBalancer.route(
          request = request,
          requestedPath = Path.unsafeFromString("prefix/").addSegments(requestedPath.segments), // prefix is hardcoded it would be taken from map that holds all prefixes
          group = Group(routeInfo.mappingContent.left.get) // temp solution
        )
      }
  }
}
