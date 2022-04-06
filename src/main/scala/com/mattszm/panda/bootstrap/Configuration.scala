package com.mattszm.panda.bootstrap

import com.avast.sst.http4s.server.Http4sBlazeServerConfig
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader
import com.avast.sst.http4s.server.pureconfig.implicits._


final case class Configuration(
                                server: Http4sBlazeServerConfig,
                              )

object Configuration {
  implicit val reader: ConfigReader[Configuration] = deriveReader
}

