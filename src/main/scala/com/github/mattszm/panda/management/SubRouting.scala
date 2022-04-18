package com.github.mattszm.panda.management

import monix.eval.Task
import org.http4s.HttpRoutes

trait SubRouting {
  def getRoutes: HttpRoutes[Task]
}

object SubRouting {
  final val API_NAME = "api"
  final val API_VERSION_1 = "v1"
}
