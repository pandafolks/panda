package com.mattszm.panda.bootstrap

import com.avast.sst.http4s.server.Http4sRouting
import monix.eval.Task
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpApp, HttpRoutes}

class Routing extends Http4sDsl[Task] {
  private val routes = HttpRoutes.of[Task] {
    case GET -> Root / "hello" => Ok(Task.eval("fds"))
  }

  val router: HttpApp[Task] = Http4sRouting.make { routes }
}
