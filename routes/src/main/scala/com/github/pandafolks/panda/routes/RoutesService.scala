package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.payload.{RoutesRemovePayload, RoutesResourcePayload}
import com.github.pandafolks.panda.utils.PersistenceError
import monix.eval.Task

trait RoutesService {

  /**
   * Returns all Routes and Prefixes present in the persistence layer as a [[RoutesResourcePayload]].
   *
   * @return                            Routes and Prefixes
   */
  def findAll(): Task[RoutesResourcePayload]

  /**
   * Saves all Routes and Prefixes delivered with the [[RoutesResourcePayload]]. The method discards all duplicates
   * and saves in the persistence layer only those that have been not present before.
   *
   * @param routesResourcePayload
   * @return                            Routes and Prefixes creation results
   */
  def save(routesResourcePayload: RoutesResourcePayload): Task[(List[Either[PersistenceError, String]], List[Either[PersistenceError, String]])]

  /**
   * Removes all requested [[entity.Mapper]] and group [[entity.Prefix]].
   * Mappers distinction is made based on routes and the HTTP methods.
   * Prefixes distinction is made based on group names.
   *
   * @param routesRemovePayload
   * @return                             Routes and Prefixes deletion results
   */
  def delete(routesRemovePayload: RoutesRemovePayload): Task[(List[Either[PersistenceError, String]], List[Either[PersistenceError, String]])]
}
