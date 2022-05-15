package com.github.mattszm.panda.gateway

import com.github.mattszm.panda.utils.SubRoutingWithNoAuth
import monix.eval.Task
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

final class ApiGatewayRouting(private val apiGateway: ApiGateway) extends Http4sDsl[Task] with SubRoutingWithNoAuth {
  private val routes = HttpRoutes.of[Task] {
    case request@GET -> "gateway" /: requestedPath => apiGateway.ask(request, requestedPath)
  }

  override def getRoutes: HttpRoutes[Task] = routes
}
