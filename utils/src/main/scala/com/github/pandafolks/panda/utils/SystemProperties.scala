package com.github.pandafolks.panda.utils

import scala.util.Try

object SystemProperties {
  object Defaults {
    final val CONSISTENT_HASHING_STATE_POSITIONS_PER_PARTICIPANT_DEFAULT: Int = 20
    final val CONSISTENT_HASHING_STATE_CLEAR_EMPTY_GROUPS_INTERVAL_IN_HOURS: Int = 12

    final val MAIN_LOG_FILE = "panda"
    final val MAIN_LOG_MAX_FILE_SIZE = "100MB"
    final val MAIN_LOG_MAX_HISTORY_IN_DAYS = "60"
    final val MAIN_LOG_TOTAL_SIZE_CAP = "10GB"

    final val GATEWAY_TRAFFIC_LOG_FILE = "gateway_traffic"
    final val GATEWAY_TRAFFIC_LOG_MAX_FILE_SIZE = "100MB"
    final val GATEWAY_TRAFFIC_LOG_MAX_HISTORY_IN_DAYS = "60"
    final val GATEWAY_TRAFFIC_LOG_TOTAL_SIZE_CAP = "10GB"
  }

  def scalaConcurrentContextMinThreads: String = System.getProperty("scala.concurrent.context.minThreads")

  def scalaConcurrentContextMaxThreads: String = System.getProperty("scala.concurrent.context.maxThreads")

  def scalaConcurrentContextNumThreads: String = System.getProperty("scala.concurrent.context.numThreads")

  /** A private key used to sign users' authentication tokens.
    *
    * Example: -Dpanda.user.token.key=5ck4kBO45606H25YUZ1f
    */
  def usersTokenKey: String = System.getProperty("panda.user.token.key")

  /** A number of points on the consistent hashing circle for a single participant. The higher the number is the more evenly the requests
    * will be spread but the performance of the adding to the circle operation will drop.
    *
    * The default value is 20.
    *
    * Example: -Dpanda.consistent.hashing.state.positions.per.participant=30
    */
  def consistentHashingStatePositionsPerParticipant: Int =
    Try { System.getProperty("panda.consistent.hashing.state.positions.per.participant").toInt }
      .getOrElse(Defaults.CONSISTENT_HASHING_STATE_POSITIONS_PER_PARTICIPANT_DEFAULT)

  /** A number of hours between each run of the background job which is responsible for clearing empty groups inside the
    * [[ConsistentHashingState#usedPositionsGroupedByGroup]] in order to reduce memory overhead. If the value is smaller or equal to '0' the
    * background job won't be launched.
    *
    * The default value is 12.
    *
    * Example: -Dpanda.consistent.hashing.state.clear.empty.groups.interval=24
    */
  def consistentHashingStateClearEmptyGroupsIntervalInHours: Int =
    Try { System.getProperty("panda.consistent.hashing.state.clear.empty.groups.interval").toInt }
      .getOrElse(Defaults.CONSISTENT_HASHING_STATE_CLEAR_EMPTY_GROUPS_INTERVAL_IN_HOURS)

  def mainLogFileName: String = Option(System.getProperty("panda.main.log.file.name")).getOrElse(Defaults.MAIN_LOG_FILE)

  def mainLogMaxFileSize: String = Option(System.getProperty("panda.main.log.max.file.size")).getOrElse(Defaults.MAIN_LOG_MAX_FILE_SIZE)

  def mainLogMaxHistoryInDays: String =
    Option(System.getProperty("panda.main.log.max.history.in.days")).getOrElse(Defaults.MAIN_LOG_MAX_HISTORY_IN_DAYS)

  def mainLogTotalSizeCap: String = Option(System.getProperty("panda.main.log.total.size.cap")).getOrElse(Defaults.MAIN_LOG_TOTAL_SIZE_CAP)

  def gatewayTrafficLogFileName: String =
    Option(System.getProperty("panda.gateway.traffic.log.file.name")).getOrElse(Defaults.GATEWAY_TRAFFIC_LOG_FILE)

  def gatewayTrafficLogMaxFileSize: String =
    Option(System.getProperty("panda.gateway.traffic.log.max.file.size")).getOrElse(Defaults.GATEWAY_TRAFFIC_LOG_MAX_FILE_SIZE)

  def gatewayTrafficLogMaxHistoryInDays: String =
    Option(System.getProperty("panda.gateway.traffic.log.max.history.in.days")).getOrElse(Defaults.GATEWAY_TRAFFIC_LOG_MAX_HISTORY_IN_DAYS)
  def gatewayTrafficLogTotalSizeCap: String =
    Option(System.getProperty("panda.gateway.traffic.log.total.size.cap")).getOrElse(Defaults.GATEWAY_TRAFFIC_LOG_TOTAL_SIZE_CAP)
}
