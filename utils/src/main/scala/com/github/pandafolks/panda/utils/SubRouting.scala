package com.github.pandafolks.panda.utils

import monix.eval.Task
import org.http4s.HttpRoutes

trait SubRouting

trait SubRoutingWithNoAuth extends SubRouting {
  def getRoutes: HttpRoutes[Task]
}

object SubRouting {
  final val API_NAME = "api"
  final val API_VERSION_1 = "v1"
}
