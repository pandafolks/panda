package com.github.mattszm.panda.db

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.config.DefaultDriverOption
import com.datastax.oss.driver.internal.core.config.typesafe.DefaultDriverConfigLoader
import com.github.mattszm.panda.bootstrap.configuration.sub.DbConfig

import java.time.Duration
import java.util

final class Session(private val config: DbConfig) {

  implicit val cqlSession: CqlSession = CqlSession.builder
    .withConfigLoader(
    DefaultDriverConfigLoader.builder()
      .withStringList(DefaultDriverOption.CONTACT_POINTS, util.Arrays.asList(config.contactPoints: _*))
      .withString(DefaultDriverOption.LOAD_BALANCING_LOCAL_DATACENTER, config.loadBalancingLocalDataCenter)
      .withString(DefaultDriverOption.AUTH_PROVIDER_CLASS, Session.PLAIN_TEXT_AUTH_PROVIDER)
      .withString(DefaultDriverOption.AUTH_PROVIDER_USER_NAME, config.username)
      .withString(DefaultDriverOption.AUTH_PROVIDER_PASSWORD, config.password)
      .withString(DefaultDriverOption.SESSION_KEYSPACE, config.keySpace)
      .withLong(DefaultDriverOption.REQUEST_TIMEOUT, config.requestTimeout)
      .withLong(DefaultDriverOption.CONNECTION_INIT_QUERY_TIMEOUT, config.connectionInitQueryTimeout)
      .withString(DefaultDriverOption.RETRY_POLICY_CLASS, Session.DEFAULT_RETRY_POLICY)
      .withString(DefaultDriverOption.RECONNECTION_POLICY_CLASS, Session.CONSTANT_RECONNECTION_POLICY)
      .withDuration(DefaultDriverOption.RECONNECTION_BASE_DELAY, Duration.ofSeconds(config.reconnectionBaseDelayInSeconds))
      .withInt(DefaultDriverOption.NETTY_IO_SIZE, 0)
      .withInt(DefaultDriverOption.NETTY_ADMIN_SIZE, 1)
      .withString(DefaultDriverOption.HEARTBEAT_INTERVAL, config.heartbeatInterval)
      .withString(DefaultDriverOption.HEARTBEAT_TIMEOUT, config.heartbeatInterval)
      .withBoolean(DefaultDriverOption.RECONNECT_ON_INIT, true)
      .build()
  ).build()
}

object Session {
  final val PLAIN_TEXT_AUTH_PROVIDER = "PlainTextAuthProvider"
  final val DEFAULT_RETRY_POLICY = "DefaultRetryPolicy"
  final val CONSTANT_RECONNECTION_POLICY = "ConstantReconnectionPolicy"
}
