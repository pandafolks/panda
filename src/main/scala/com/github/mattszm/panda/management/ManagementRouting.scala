package com.github.mattszm.panda.management

import com.github.mattszm.panda.management.SubRouting.{API_NAME, API_VERSION_1}
import monix.eval.Task
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

final class ManagementRouting extends Http4sDsl[Task] with SubRouting {
  private val routes = HttpRoutes.of[Task] {
    case _@GET -> Root / API_NAME / API_VERSION_1 / "management" => Ok("response")
  }

  override def getRoutes: HttpRoutes[Task] = routes
}
