package com.github.mattszm.panda.bootstrap

import com.avast.sst.http4s.server.Http4sRouting
import com.github.mattszm.panda.gateway.ApiGateway
import monix.eval.Task
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpApp, HttpRoutes}

final class PrimaryRouting(private val apiGateway: ApiGateway) extends Http4sDsl[Task] {
  private val routes = HttpRoutes.of[Task] {
    case request @ GET -> "gateway" /: requestedPath => apiGateway.ask(request, requestedPath)
  }

  val router: HttpApp[Task] = Http4sRouting.make { routes }
}
