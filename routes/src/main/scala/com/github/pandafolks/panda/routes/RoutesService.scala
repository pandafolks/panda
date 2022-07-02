package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.dto.RoutesResourceDto
import com.github.pandafolks.panda.utils.PersistenceError
import monix.eval.Task

trait RoutesService {

  // todo mszmal: add docs
  def saveRoutes(routesResourceDto: RoutesResourceDto): Task[List[Either[PersistenceError, String]]]
}
