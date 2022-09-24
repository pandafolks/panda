package com.github.pandafolks.panda.bootstrap.configuration

import com.avast.sst.http4s.client.Http4sBlazeClientConfig
import com.avast.sst.http4s.server.Http4sBlazeServerConfig
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.{deriveEnumerationReader, deriveReader}
import com.avast.sst.http4s.server.pureconfig.implicits._
import com.avast.sst.http4s.client.pureconfig.implicits._
import com.github.pandafolks.panda.db.DbConfig
import com.github.pandafolks.panda.gateway.{GatewayConfig, LoadBalanceAlgorithm}
import com.github.pandafolks.panda.healthcheck.HealthCheckConfig
import com.github.pandafolks.panda.user.UserCredentials
import com.github.pandafolks.panda.user.token.TokensConfig
import pureconfig.generic.auto._

final case class AppConfiguration(
                                   appServer: Http4sBlazeServerConfig,
                                   gatewayClient: Http4sBlazeClientConfig,
                                   internalClient: Http4sBlazeClientConfig,
                                   gateway: GatewayConfig,
                                   db: DbConfig,
                                   consistency: ConsistencyConfig,
                                   healthCheckConfig: HealthCheckConfig,
                                   authTokens: TokensConfig,
                                   initUser: UserCredentials,
                                 )

object AppConfiguration {
  implicit val LoadBalanceAlgorithmConverter: ConfigReader[LoadBalanceAlgorithm] = deriveEnumerationReader[LoadBalanceAlgorithm]
  implicit val reader: ConfigReader[AppConfiguration] = deriveReader
}
