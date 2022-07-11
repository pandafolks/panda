package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.entity.Prefix
import com.github.pandafolks.panda.utils.PersistenceError
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task
import monix.reactive.Observable

trait PrefixDao {

  /**
   * Saves in the persistence layer the [[Prefix]] with the requested group name and prefix
   * if the one for the specified group name does not exist.
   *
   * @param groupName
   * @param prefix
   * @param prefixOperator    [[Prefix]] DB entry point
   *
   * @return                  Either group name if saved successfully or PersistenceError if the error during saving occurred
   */
  def savePrefix(groupName: String, prefix: String)(prefixOperator: CollectionOperator[Prefix]): Task[Either[PersistenceError, String]]

  /**
   * Saves in the persistence layer the [[Prefix]] with the requested group name and prefix
   * if the one for the specified group name does not exist.
   * If exists the update is performed. The Prefix recognition is made based on the group name.
   *
   * @param groupName
   * @param prefix
   * @param prefixOperator    [[Prefix]] DB entry point
   *
   * @return                  Either group name if saved successfully or PersistenceError if the error during saving occurred
   */
  def saveOrUpdatePrefix(groupName: String, prefix: String)(prefixOperator: CollectionOperator[Prefix]): Task[Either[PersistenceError, String]]

  /**
   * Returns all [[Prefix]] present in the persistence layer.
   *
   *
   * @param prefixOperator    [[Prefix]] DB entry point
   *
   * @return                  Observable of [[Prefix]]
   */
  def findAll(prefixOperator: CollectionOperator[Prefix]): Observable[Prefix]

  /**
   * Removes [[Prefix]] associated with requested group name.
   *
   * @param groupName
   * @param prefixOperator    [[Prefix]] DB entry point
   * @return                  Either group name if deleted successfully or PersistenceError if the error during deletion occurred
   */
  def delete(groupName: String)(prefixOperator: CollectionOperator[Prefix]): Task[Either[PersistenceError, String]]
}
