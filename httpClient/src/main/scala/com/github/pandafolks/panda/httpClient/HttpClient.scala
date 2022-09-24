package com.github.pandafolks.panda.httpClient

import cats.effect.{ConcurrentEffect, Resource}
import com.avast.sst.http4s.client.{Http4sBlazeClientConfig, Http4sBlazeClientModule}
import org.http4s.client.Client

import javax.net.ssl.SSLContext
import scala.concurrent.ExecutionContext

object HttpClient {
  def create[F[_] : ConcurrentEffect](
                                       config: Http4sBlazeClientConfig,
                                       executionContext: ExecutionContext,
                                       sslContext: Option[SSLContext] = None
                                     ): Resource[F, Client[F]] =
    Http4sBlazeClientModule.make[F](config = config, executionContext = executionContext, sslContext = sslContext)
}
