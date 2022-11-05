package com.github.pandafolks.panda.bootstrap.configuration

import com.avast.sst.http4s.client.Http4sEmberClientConfig
import com.avast.sst.http4s.server.Http4sEmberServerConfig
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.{deriveEnumerationReader, deriveReader}
import com.avast.sst.http4s.server.pureconfig.ember.implicits._
import com.avast.sst.http4s.client.pureconfig.ember.implicits._
import com.github.pandafolks.panda.db.DbConfig
import com.github.pandafolks.panda.gateway.{GatewayConfig, LoadBalanceAlgorithm}
import com.github.pandafolks.panda.healthcheck.HealthCheckConfig
import com.github.pandafolks.panda.user.UserCredentials
import com.github.pandafolks.panda.user.token.TokensConfig
import pureconfig.generic.auto._

final case class AppConfiguration(
    appServer: Http4sEmberServerConfig,
    gatewayClient: Http4sEmberClientConfig,
    internalClient: Http4sEmberClientConfig,
    gateway: GatewayConfig,
    db: DbConfig,
    consistency: ConsistencyConfig,
    healthCheckConfig: HealthCheckConfig,
    authTokens: TokensConfig,
    initUser: UserCredentials
)

object AppConfiguration {
  implicit val LoadBalanceAlgorithmConverter: ConfigReader[LoadBalanceAlgorithm] =
    deriveEnumerationReader[LoadBalanceAlgorithm]
  implicit val reader: ConfigReader[AppConfiguration] = deriveReader
}
