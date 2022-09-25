package com.github.pandafolks.panda.user

import com.github.pandafolks.panda.utils.routing.SubRouting
import monix.eval.Task
import org.http4s.AuthedRoutes

trait SubRoutingWithAuth extends SubRouting {
  def getRoutesWithAuth: AuthedRoutes[User, Task]
}
