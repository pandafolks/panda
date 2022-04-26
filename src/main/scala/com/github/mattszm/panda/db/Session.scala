package com.github.mattszm.panda.db

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.config.DefaultDriverOption
import com.datastax.oss.driver.internal.core.config.typesafe.DefaultDriverConfigLoader
import com.github.mattszm.panda.bootstrap.configuration.sub.DbConfig

import java.time.Duration
import java.util

final class Session(private val config: DbConfig) {

  //todo mszmal: migrate all strings to DbConfig
  implicit val cqlSession: CqlSession = CqlSession.builder
    .withConfigLoader(
    DefaultDriverConfigLoader.builder()
      .withStringList(DefaultDriverOption.CONTACT_POINTS, util.Arrays.asList(config.contactPoints: _*))
      .withString(DefaultDriverOption.LOAD_BALANCING_LOCAL_DATACENTER, config.loadBalancingLocalDataCenter)
      .withString(DefaultDriverOption.AUTH_PROVIDER_CLASS, "PlainTextAuthProvider")
      .withString(DefaultDriverOption.AUTH_PROVIDER_USER_NAME, config.username)
      .withString(DefaultDriverOption.AUTH_PROVIDER_PASSWORD, config.password)
      .withString(DefaultDriverOption.SESSION_KEYSPACE, config.keySpace)
      .withLong(DefaultDriverOption.REQUEST_TIMEOUT, config.requestTimeout)
      .withLong(DefaultDriverOption.CONNECTION_INIT_QUERY_TIMEOUT, config.requestTimeout)
      .withString(DefaultDriverOption.RETRY_POLICY_CLASS, "DefaultRetryPolicy")
      .withString(DefaultDriverOption.RECONNECTION_POLICY_CLASS, "ConstantReconnectionPolicy")
      .withDuration(DefaultDriverOption.RECONNECTION_BASE_DELAY, Duration.ofSeconds(config.reconnectionBaseDelayInSeconds))
      .withInt(DefaultDriverOption.NETTY_IO_SIZE, 0)
      .withInt(DefaultDriverOption.NETTY_ADMIN_SIZE, 1)
      .withString(DefaultDriverOption.HEARTBEAT_INTERVAL, "30 seconds")
      .withString(DefaultDriverOption.HEARTBEAT_TIMEOUT, "700 milliseconds")
      .withBoolean(DefaultDriverOption.RECONNECT_ON_INIT, true)
      .build()
  ).build()
}
