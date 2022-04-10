package com.mattszm.panda.bootstrap.configuration

import com.avast.sst.http4s.client.Http4sBlazeClientConfig
import com.avast.sst.http4s.server.Http4sBlazeServerConfig
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader
import com.avast.sst.http4s.server.pureconfig.implicits._
import com.avast.sst.http4s.client.pureconfig.implicits._
import com.mattszm.panda.bootstrap.configuration.sub.GatewayConfig
import pureconfig.generic.auto._

//implicit imports
//import com.avast.sst.http4s.server.pureconfig.implicits._
//import com.avast.sst.http4s.client.pureconfig.implicits._
//import pureconfig.generic.auto._

final case class AppConfiguration(
                                appServer: Http4sBlazeServerConfig,
                                gatewayClient: Http4sBlazeClientConfig,
                                gateway: GatewayConfig
                              )

object AppConfiguration {
  implicit val reader: ConfigReader[AppConfiguration] = deriveReader
}
