package com.github.pandafolks.panda.nodestracker

import com.github.pandafolks.panda.utils.PersistenceError
import monix.eval.Task

trait NodeTrackerDao {
  /**
   * Inserts a randomly generated [[Node]]. This inserted node corresponds to the instance from which it was inserted.
   *
   * @return               Node ID if inserted successfully or PersistenceError if the error during inserting occurred
   */
  def register(): Task[Either[PersistenceError, String]]

  /**
   * Finds [[Node]]  with the requested ID and refreshes its last update timestamp.
   *
   * @param nodeId         Node Identifier based on which nodes are recognized.
   * @return               Empty if updated successfully or PersistenceError if the error during updating occurred
   */
  def notify(nodeId: String): Task[Either[PersistenceError, Unit]]
}