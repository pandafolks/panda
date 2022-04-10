package com.mattszm.panda.bootstrap

import com.avast.sst.http4s.server.Http4sRouting
import com.mattszm.panda.gateway.ApiGateway
import monix.eval.Task
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpApp, HttpRoutes}

class PrimaryRouting(private final val apiGateway: ApiGateway) extends Http4sDsl[Task] {
  private val routes = HttpRoutes.of[Task] {
    case req @ GET -> "gateway" /: requestedPath => apiGateway.getResponse(req, requestedPath)
  }

  val router: HttpApp[Task] = Http4sRouting.make { routes }
}
