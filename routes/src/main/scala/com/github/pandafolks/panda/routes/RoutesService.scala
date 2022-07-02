package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.utils.PersistenceError
import monix.eval.Task

trait RoutesService {

  def saveRoutes(): Task[Either[PersistenceError, String]]
}
