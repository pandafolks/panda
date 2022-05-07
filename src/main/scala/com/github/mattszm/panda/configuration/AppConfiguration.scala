package com.github.mattszm.panda.configuration

import com.avast.sst.http4s.client.Http4sBlazeClientConfig
import com.avast.sst.http4s.server.Http4sBlazeServerConfig
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.{deriveEnumerationReader, deriveReader}
import com.avast.sst.http4s.server.pureconfig.implicits._
import com.avast.sst.http4s.client.pureconfig.implicits._
import com.github.mattszm.panda.configuration.sub.{DbConfig, GatewayConfig, LoadBalanceAlgorithm}
import com.github.mattszm.panda.user.UserCredentials
import pureconfig.generic.auto._

final case class AppConfiguration(
                                appServer: Http4sBlazeServerConfig,
                                gatewayClient: Http4sBlazeClientConfig,
                                gateway: GatewayConfig,
                                db: DbConfig,
                                initUser: UserCredentials
                              )

object AppConfiguration {
  implicit val LoadBalanceAlgorithmConverter: ConfigReader[LoadBalanceAlgorithm] = deriveEnumerationReader[LoadBalanceAlgorithm]
  implicit val reader: ConfigReader[AppConfiguration] = deriveReader
}
