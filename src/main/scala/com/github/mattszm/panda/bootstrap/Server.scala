package com.github.mattszm.panda.bootstrap

import cats.effect.Resource
import com.avast.sst.bundle.MonixServerApp
import com.avast.sst.http4s.client.Http4sBlazeClientModule
import com.avast.sst.http4s.server.Http4sBlazeServerModule
import com.avast.sst.pureconfig.PureConfigModule
import com.github.mattszm.panda.bootstrap.configuration.AppConfiguration
import com.github.mattszm.panda.gateway.BaseApiGatewayImpl
import com.github.mattszm.panda.participant.{Participant, ParticipantsCacheImpl}
import com.github.mattszm.panda.routes.{Group, RoutesTreeImpl}
import com.github.mattszm.panda.routes.dto.RoutesMappingInitializationDto
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
      routesTree = RoutesTreeImpl.construct(routesMappingInitializationEntries)

      httpGatewayClient <- Http4sBlazeClientModule.make[Task](appConfiguration.gatewayClient, global)
      tempParticipants = List(
        Participant("127.0.0.1", 3000, Group("cars")),
        Participant("localhost", 3001, Group("cars")),
        Participant("127.0.0.1", 4000, Group("planes"))
      )
      loadBalancer = appConfiguration.gateway.loadBalanceAlgorithm.create(
        client = httpGatewayClient,
        participantsCache = new ParticipantsCacheImpl(tempParticipants)
      )

      apiGateway = new BaseApiGatewayImpl(loadBalancer, routesTree)
      primaryRouting = new PrimaryRouting(apiGateway)
      server <- Http4sBlazeServerModule.make[Task](
        appConfiguration.appServer,
        primaryRouting.router,
        global
      )
    } yield server
}
