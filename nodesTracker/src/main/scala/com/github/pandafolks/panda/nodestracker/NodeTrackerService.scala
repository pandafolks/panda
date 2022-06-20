package com.github.pandafolks.panda.nodestracker

import monix.eval.Task

trait NodeTrackerService {
  /**
   * Returns [[Node]] ID of the instance it is called on.
   *
   * @return               Node ID that identifies the Panda instance
   */
  def getNodeId: String

  /**
   * Returns all currently working Panda [[Node]]. Node is working if the tracker was notified about its existence
   * at least X/2 seconds from this moment, whereas X means full consistency max delay specified
   * via configuration (same across all nodes).
   *
   * @return               All found nodes that meet the criteria of working
   */
  def getWorkingNodes: Task[List[Node]]
}
