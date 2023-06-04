package com.github.pandafolks.panda.bootstrap

import ch.qos.logback.classic.{Level, Logger, LoggerContext}
import ch.qos.logback.classic.spi.LoggerContextListener
import ch.qos.logback.core.spi.{ContextAwareBase, LifeCycle}
import com.github.pandafolks.panda.utils.SystemProperties

final class LoggerStartupListener extends ContextAwareBase with LoggerContextListener with LifeCycle {
  private var started = false

  @Override
  def start(): Unit = {
    if (started) return

    val context = getContext()

    // main logger
    context.putProperty("MAIN_LOG_FILE_NAME", SystemProperties.mainLogFileName)
    context.putProperty("MAIN_LOG_MAX_FILE_SIZE", SystemProperties.mainLogMaxFileSize)
    context.putProperty("MAIN_LOG_MAX_HISTORY_DAYS", SystemProperties.mainLogMaxHistoryInDays)
    context.putProperty("MAIN_LOG_TOTAL_SIZE_CAP", SystemProperties.mainLogTotalSizeCap)

    // gateway logger
    context.putProperty("GATEWAY_TRAFFIC_LOG_FILE_NAME", SystemProperties.gatewayTrafficLogFileName)
    context.putProperty("GATEWAY_TRAFFIC_LOG_MAX_FILE_SIZE", SystemProperties.gatewayTrafficLogMaxFileSize)
    context.putProperty("GATEWAY_TRAFFIC_LOG_MAX_HISTORY_DAYS", SystemProperties.gatewayTrafficLogMaxHistoryInDays)
    context.putProperty("GATEWAY_TRAFFIC_LOG_TOTAL_SIZE_CAP", SystemProperties.gatewayTrafficLogTotalSizeCap)

    started = true
  }

  override def stop(): Unit = {}

  override def isStarted(): Boolean = started

  override def isResetResistant: Boolean = true

  override def onStart(context: LoggerContext): Unit = {}

  override def onReset(context: LoggerContext): Unit = {}

  override def onStop(context: LoggerContext): Unit = {}

  override def onLevelChange(logger: Logger, level: Level): Unit = {}
}
