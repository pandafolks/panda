package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.dto.RoutesResourceDto
import com.github.pandafolks.panda.utils.PersistenceError
import monix.eval.Task

trait RoutesService {

  /**
   * Saves all Routes and Prefixes delivered with the [[RoutesResourceDto]]. The method discards all duplicates
   * and saves in the persistence layer only those that have been not present before.
   *
   * @param routesResourceDto
   *
   * @return                      Routes and Prefixes creation results
   */
  def saveRoutes(routesResourceDto: RoutesResourceDto): Task[(List[Either[PersistenceError, String]], List[Either[PersistenceError, String]])]

  /**
   * Returns all Routes and Prefixes present in the persistence layer as a [[RoutesResourceDto]].
   *
   * @return                      Routes and Prefixes creation results
   */
  def findAll(): Task[RoutesResourceDto]
}
