package com.github.pandafolks.panda.nodestracker

import com.github.pandafolks.panda.utils.PersistenceError
import monix.eval.Task
import org.bson.types.ObjectId

trait JobDao {

  /** Finds [[Job]] with the requested name.
    *
    * @param jobName
    *   a unique job name
    * @return
    *   [[Job]] if found, empty otherwise
    */
  def find(jobName: String): Task[Option[Job]]

  /** Assigns a [[Node]] with the requested nodeId to the [[Job]] with the requested name.
    *
    * @param jobName
    *   a unique job name
    * @param nodeId
    *   identifier of a node that will be responsible for the job
    * @return
    *   Empty if updated successfully or [[PersistenceError]] if the error during update occurred
    */
  def assignNodeToJob(jobName: String, nodeId: ObjectId): Task[Either[PersistenceError, Unit]]

}
