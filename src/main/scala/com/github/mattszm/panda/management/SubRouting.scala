package com.github.mattszm.panda.management

import com.github.mattszm.panda.user.User
import monix.eval.Task
import org.http4s.{AuthedRoutes, HttpRoutes}

sealed trait SubRouting

trait SubRoutingWithNoAuth extends SubRouting {
  def getRoutes: HttpRoutes[Task]
}

trait SubRoutingWithAuth extends SubRouting {
  def getRoutes: AuthedRoutes[User, Task]
}

object SubRouting {
  final val API_NAME = "api"
  final val API_VERSION_1 = "v1"
}
