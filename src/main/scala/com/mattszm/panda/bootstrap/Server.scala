package com.mattszm.panda.bootstrap

import cats.effect.Resource
import com.avast.sst.bundle.MonixServerApp
import com.avast.sst.http4s.client.Http4sBlazeClientModule
import com.avast.sst.http4s.server.Http4sBlazeServerModule
import com.avast.sst.pureconfig.PureConfigModule
import com.mattszm.panda.bootstrap.configuration.AppConfiguration
import com.mattszm.panda.gateway.BaseApiGatewayImpl
import com.mattszm.panda.routes.RoutesTree
import com.mattszm.panda.routes.dto.RoutesMappingInitializationDto
import monix.eval.Task
import org.http4s.server.Server

import scala.concurrent.ExecutionContext.global
import scala.io.Source

object Server extends MonixServerApp {
  override def program: Resource[Task, Server] =
    for {
      appConfiguration <- Resource.eval(PureConfigModule.makeOrRaise[Task, AppConfiguration])
      routesMappingConfiguration <- Resource.eval(Task.evalOnce(
        ujson.read(Source.fromResource(appConfiguration.gateway.mappingFile).mkString)))
      routesMappingInitializationEntries = RoutesMappingInitializationDto.of(routesMappingConfiguration)
      routesTree = RoutesTree.construct(routesMappingInitializationEntries)

      httpGatewayClient <- Http4sBlazeClientModule.make[Task](appConfiguration.gatewayClient, global)
      apiGateway = new BaseApiGatewayImpl(httpGatewayClient, routesTree)
      primaryRouting = new PrimaryRouting(apiGateway)
      server <- Http4sBlazeServerModule.make[Task](
        appConfiguration.appServer,
        primaryRouting.router,
        global
      )
    } yield server
}
