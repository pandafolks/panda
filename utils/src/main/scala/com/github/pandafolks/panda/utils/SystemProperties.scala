package com.github.pandafolks.panda.utils

import scala.util.Try

object SystemProperties {
  object Defaults {
    final val CONSISTENT_HASHING_STATE_POSITIONS_PER_PARTICIPANT_DEFAULT: Int = 20
    final val CONSISTENT_HASHING_STATE_CLEAR_EMPTY_GROUPS_INTERVAL_IN_HOURS: Int = 12
  }

  def scalaConcurrentContextMinThreads: String = System.getProperty("scala.concurrent.context.minThreads")

  def scalaConcurrentContextMaxThreads: String = System.getProperty("scala.concurrent.context.maxThreads")

  def scalaConcurrentContextNumThreads: String = System.getProperty("scala.concurrent.context.numThreads")

  /**
   * A private key used to sign users' authentication tokens.
   *
   * Example:   -Dpanda.user.token.key=5ck4kBO45606H25YUZ1f
   */
  def usersTokenKey: String = System.getProperty("panda.user.token.key")

  /**
   * A number of points on the consistent hashing circle for a single participant.
   * The higher the number is the more evenly the requests will be spread but the performance of the adding
   * to the circle operation will drop.
   *
   * Example:   -Dpanda.consistent.hashing.state.positions.per.participant=30
   */
  def consistentHashingStatePositionsPerParticipant: Int =
    Try { System.getProperty("panda.consistent.hashing.state.positions.per.participant").toInt }
      .getOrElse(Defaults.CONSISTENT_HASHING_STATE_POSITIONS_PER_PARTICIPANT_DEFAULT)

  /**
   * A number of hours between each run of the background job which is responsible for clearing empty groups
   * inside the [[ConsistentHashingState#usedPositionsGroupedByGroup]] in order to reduce memory overhead.
   * If the value is smaller or equal to '0' the background job won't be launched.
   *
   * Example:   -Dpanda.consistent.hashing.state.clear.empty.groups.interval=24
   */
  def consistentHashingStateClearEmptyGroupsIntervalInHours: Int =
    Try { System.getProperty("panda.consistent.hashing.state.clear.empty.groups.interval").toInt }
      .getOrElse(Defaults.CONSISTENT_HASHING_STATE_CLEAR_EMPTY_GROUPS_INTERVAL_IN_HOURS)
}
