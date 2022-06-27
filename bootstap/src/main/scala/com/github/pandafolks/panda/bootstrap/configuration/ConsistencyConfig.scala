package com.github.pandafolks.panda.bootstrap.configuration

final case class ConsistencyConfig(fullConsistencyMaxDelay: Int) { // in seconds
  def getRealFullConsistencyMaxDelayInMillis: Int = ((fullConsistencyMaxDelay.toDouble - 1).max(0.5) * 1000).toInt // always subtract one, but 0.5 seconds is the smallest possible value
}
