package com.github.pandafolks.panda.nodestracker

import monix.eval.Task
import org.bson.types.ObjectId

trait NodeTrackerService {

  /** Returns [[Node]] ID of the instance it is called on.
    *
    * @return
    *   Node ID that identifies the Panda instance
    */
  def getNodeId: String

  /** Returns all currently working Panda [[Node]]. Node is working if the tracker was notified about its existence at least X/2 seconds
    * from this moment, whereas X means full consistency max delay specified via configuration (same across all nodes). Nodes are sorted by
    * ID in ascending order.
    *
    * @return
    *   All found nodes that meet the criteria of working
    */
  def getWorkingNodes: Task[List[Node]]

  /** Returns whether a node with the requested ID is a working one.
    *
    * @param nodeId
    *   Node Identifier based on which nodes are recognized
    * @return
    *   True if the node is working, false otherwise
    */
  def isNodeWorking(nodeId: ObjectId): Task[Boolean]

  /** Returns whether a current node (the node this code is executed on) is the one responsible for the job with requested job name.
    *
    * @param nodeId
    *   Node Identifier based on which nodes are recognized
    * @return
    *   True if the node is working, false otherwise
    */
  def isCurrentNodeResponsibleForJob(jobName: String): Task[Boolean]
}
