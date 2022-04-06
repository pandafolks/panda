package com.mattszm.panda.bootstrap

import cats.effect.Resource
import com.avast.sst.bundle.MonixServerApp
import com.avast.sst.http4s.server.Http4sBlazeServerModule
import com.avast.sst.pureconfig.PureConfigModule
import monix.eval.Task
import org.http4s.server.Server

import scala.concurrent.ExecutionContext.global

object Server extends MonixServerApp {
  override def program: Resource[Task, Server] =
    for {
      configuration <- Resource.eval(PureConfigModule.makeOrRaise[Task, Configuration])
      server <- Http4sBlazeServerModule.make[Task](configuration.server, new Routing().router, global)
    } yield server
}
