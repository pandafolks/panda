package com.github.mattszm.panda.gateway

import com.github.mattszm.panda.loadbalancer.LoadBalancer
import com.github.mattszm.panda.routes.RoutesTrees
import monix.eval.Task
import org.http4s.Uri.Path
import org.http4s.{Request, Response}
import org.slf4j.LoggerFactory

final class BaseApiGatewayImpl(
                                private val loadBalancer: LoadBalancer,
                                private val routesTrees: RoutesTrees,
                              ) extends ApiGateway {
  private val logger = LoggerFactory.getLogger(getClass.getName)

  override def ask(request: Request[Task], requestedPath: Path): Task[Response[Task]] =
    Task.eval(routesTrees.get.get.specifyGroup(requestedPath)).flatMap { //todo: handle post, choose tree based on http method
      case None =>
        logger.debug("\"" + requestedPath.renderString + "\"" + " was not recognized as a supported path")
        Response.notFoundFor(request)
      case Some(groupInfo) => loadBalancer.route(
        request = request,
        requestedPath = groupInfo.prefix.addSegments(requestedPath.segments),
        group = groupInfo.group
      )
    }
}
