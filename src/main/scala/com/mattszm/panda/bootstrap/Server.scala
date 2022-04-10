package com.mattszm.panda.bootstrap

import cats.effect.Resource
import com.avast.sst.bundle.MonixServerApp
import com.avast.sst.http4s.client.Http4sBlazeClientModule
import com.avast.sst.http4s.server.Http4sBlazeServerModule
import com.avast.sst.pureconfig.PureConfigModule
import com.mattszm.panda.bootstrap.configuration.AppConfiguration
import com.mattszm.panda.gateway.BaseApiGatewayImpl
import com.mattszm.panda.gateway.dto.GatewayMappingInitializationDto
import monix.eval.Task
import org.http4s.server.Server

import scala.concurrent.ExecutionContext.global
import scala.io.Source

object Server extends MonixServerApp {
  override def program: Resource[Task, Server] =
    for {
      appConfiguration <- Resource.eval(PureConfigModule.makeOrRaise[Task, AppConfiguration])
      gatewayMappingConfiguration <- Resource.eval(Task.evalOnce(
        ujson.read(Source.fromResource(appConfiguration.gateway.mappingFile).mkString)))
      gatewayMappingInitializationEntries = GatewayMappingInitializationDto.of(gatewayMappingConfiguration)

      httpGatewayClient <- Http4sBlazeClientModule.make[Task](appConfiguration.gatewayClient, global)
      apiGateway = new BaseApiGatewayImpl(gatewayMappingInitializationEntries, httpGatewayClient)
      primaryRouting = new PrimaryRouting(apiGateway)
      server <- Http4sBlazeServerModule.make[Task](
        appConfiguration.appServer,
        primaryRouting.router,
        global
      )
    } yield server
}
