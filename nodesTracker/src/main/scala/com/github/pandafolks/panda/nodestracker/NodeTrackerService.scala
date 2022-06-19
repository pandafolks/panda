package com.github.pandafolks.panda.nodestracker

trait NodeTrackerService {
  /**
   * Returns Node ID of the instance it is called on.
   *
   * @return               Node ID that identifies the Panda instance
   */
  def getNodeId: String
}
