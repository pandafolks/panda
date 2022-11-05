package com.github.pandafolks.panda.gateway

import com.github.pandafolks.panda.utils.routing.SubRoutingWithNoAuth
import monix.eval.Task
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.middleware.Logger

final class ApiGatewayRouting(private val apiGateway: ApiGateway) extends Http4sDsl[Task] with SubRoutingWithNoAuth {
  private val routes = HttpRoutes.of[Task] { case request @ _ -> "gateway" /: requestedPath =>
    apiGateway.ask(request, requestedPath)
  }

  private val routesWihLogging: HttpRoutes[Task] = Logger.httpRoutes(logHeaders = true, logBody = false)(routes)

  override def getRoutes: HttpRoutes[Task] = routesWihLogging
}
