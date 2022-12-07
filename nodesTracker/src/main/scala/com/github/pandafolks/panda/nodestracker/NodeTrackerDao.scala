package com.github.pandafolks.panda.nodestracker

import com.github.pandafolks.panda.utils.PersistenceError
import monix.eval.Task
import org.bson.types.ObjectId

trait NodeTrackerDao {

  /** Inserts a randomly generated [[Node]]. This inserted node corresponds to the instance from which it was inserted.
    *
    * @return
    *   Node ID if inserted successfully or [[PersistenceError]] if the error during inserting occurred
    */
  def register(): Task[Either[PersistenceError, String]]

  /** Finds [[Node]] with the requested ID and refreshes its last update timestamp.
    *
    * @param nodeId
    *   Node Identifier based on which nodes are recognized
    * @return
    *   Empty if updated successfully or [[PersistenceError]] if the error during updating occurred
    */
  def notify(nodeId: String): Task[Either[PersistenceError, Unit]]

  /** Get all [[Node]] with the timestamp field higher or equal to the current time minus deviation. Nodes are sorted by
    * ID in ascending order.
    *
    * @param deviation
    *   deviation expressed in milliseconds based on which the filtration of working [[Node]]s is carried out
    * @return
    *   All found nodes
    */
  def getNodes(deviation: Long): Task[List[Node]]

  /** Returns whether a [[Node]] with the requested ID is a working one.
    *
    * @param nodeId
    *   Node Identifier based on which nodes are recognized
    * @param deviation
    *   deviation expressed in milliseconds based on which the filtration of working nodes is carried out
    * @return
    *   True if the node is working, false otherwise
    */
  def isNodeWorking(nodeId: ObjectId, deviation: Long): Task[Boolean]
}
