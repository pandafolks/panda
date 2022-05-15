package com.github.mattszm.panda.user

import com.github.mattszm.panda.utils.SubRouting
import monix.eval.Task
import org.http4s.AuthedRoutes

trait SubRoutingWithAuth extends SubRouting {
  def getRoutes: AuthedRoutes[User, Task]
}
