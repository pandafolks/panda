package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.filter.StandaloneFilter
import com.github.pandafolks.panda.routes.payload.{MapperRecordPayload, RoutesRemovePayload, RoutesResourcePayload}
import com.github.pandafolks.panda.utils.PersistenceError
import monix.eval.Task

trait RoutesService {

  /**
   * Returns all [[entity.Mapper]] and [[entity.Prefix]] present in the persistence layer as a [[RoutesResourcePayload]].
   *
   * @return                            Mappers and Prefixes
   */
  def findAll(standaloneFilter: StandaloneFilter = StandaloneFilter.All): Task[RoutesResourcePayload]

  /**
   * Returns all [[entity.Mapper]] present in the persistence layer as a map with keys being Routes.
   *
   * @return                            Mappers
   */
  def findAllMappers(standaloneFilter: StandaloneFilter = StandaloneFilter.All): Task[List[(String, MapperRecordPayload)]]

  /**
   * Returns all [[entity.Prefix]] present in the persistence layer as a map with keys being group names.
   *
   * @return                            Prefixes
   */
  def findAllPrefixes(): Task[Map[String, String]]

  /**
   * Returns all [[entity.Mapper]] and [[entity.Prefix]] present in the persistence layer linked to the specified
   * group name as a [[RoutesResourcePayload]].
   *
   * @param groupName
   * @return                            Mappers and Prefixes
   */
  def findByGroup(groupName: String, standaloneFilter: StandaloneFilter = StandaloneFilter.All): Task[RoutesResourcePayload]

  /**
   * Saves all [[entity.Mapper]] and [[entity.Prefix]] delivered with the [[RoutesResourcePayload]].
   * The method discards all duplicates and saves in the persistence layer only those that have been not present before.
   *
   * @param routesResourcePayload
   * @return                            Mappers and Prefixes creation results
   */
  def save(routesResourcePayload: RoutesResourcePayload): Task[(List[Either[PersistenceError, String]], List[Either[PersistenceError, String]])]

  /**
   * Saves all [[entity.Mapper]] and [[entity.Prefix]] delivered with the [[RoutesResourcePayload]].
   * The method saves new entries and updates those already present.
   * Mapper recognition is made based on the route, Prefix recognition is made based on the group name.
   *
   * @param routesResourcePayload
   * @return                            Mappers and Prefixes creation results
   */
  def saveWithOverrides(routesResourcePayload: RoutesResourcePayload): Task[(List[Either[PersistenceError, String]], List[Either[PersistenceError, String]])]

  /**
   * Removes all requested [[entity.Mapper]] and group [[entity.Prefix]].
   * Mappers distinction is made based on routes and the HTTP methods.
   * Prefixes distinction is made based on group names.
   *
   * @param routesRemovePayload
   * @return                             Mappers and Prefixes deletion results
   */
  def delete(routesRemovePayload: RoutesRemovePayload): Task[(List[Either[PersistenceError, String]], List[Either[PersistenceError, String]])]
}
