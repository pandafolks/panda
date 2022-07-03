package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.mappers.Prefix
import com.github.pandafolks.panda.utils.PersistenceError
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task

trait PrefixesDao {

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
}
