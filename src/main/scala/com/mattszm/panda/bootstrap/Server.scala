package com.mattszm.panda.bootstrap

import cats.effect.Resource
import com.avast.sst.bundle.MonixServerApp
import com.avast.sst.http4s.client.Http4sBlazeClientModule
import com.avast.sst.http4s.server.Http4sBlazeServerModule
import com.avast.sst.pureconfig.PureConfigModule
import com.mattszm.panda.bootstrap.configuration.AppConfiguration
import com.mattszm.panda.gateway.BaseApiGateway
import monix.eval.Task
import org.http4s.server.Server

import scala.concurrent.ExecutionContext.global
import scala.io.Source

object Server extends MonixServerApp {
  override def program: Resource[Task, Server] =
    for {
      appConfiguration <- Resource.eval(PureConfigModule.makeOrRaise[Task, AppConfiguration])
      gatewayConfiguration <- Resource.eval(Task.evalOnce(
        ujson.read(Source.fromResource(appConfiguration.gateway.configurationFile).mkString)))
      httpGatewayClient <- Http4sBlazeClientModule.make[Task](appConfiguration.gatewayClient, global)
      apiGateway = new BaseApiGateway(gatewayConfiguration, httpGatewayClient)
      server <- Http4sBlazeServerModule.make[Task](
        appConfiguration.appServer,
        new PrimaryRouting(apiGateway).router,
        global
      )
    } yield server
}
