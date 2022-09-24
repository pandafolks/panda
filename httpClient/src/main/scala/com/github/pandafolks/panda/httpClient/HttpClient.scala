package com.github.pandafolks.panda.httpClient

import cats.effect.{Blocker, Concurrent, ContextShift, Resource, Timer}
import com.avast.sst.http4s.client.{Http4sEmberClientConfig, Http4sEmberClientModule}
import fs2.io.tls.TLSContext
import monix.eval.Task
import org.http4s.client.Client

object HttpClient {
  private def create[F[_] : Concurrent : Timer : ContextShift](
                                                                config: Http4sEmberClientConfig,
                                                                blocker: Option[Blocker] = None,
                                                                tlsContext: Option[TLSContext] = None
                                                              ): Resource[F, Client[F]] =
    Http4sEmberClientModule.make[F](config = config, blocker = blocker, tlsContext = tlsContext)

  def createMonixBased(
                        config: Http4sEmberClientConfig
                      ): Resource[Task, Client[Task]] =
    HttpClient.create[Task](config = config)
}
