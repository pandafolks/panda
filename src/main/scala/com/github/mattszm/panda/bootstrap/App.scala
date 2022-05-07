package com.github.mattszm.panda.bootstrap

import cats.effect.Resource
import cats.implicits.toSemigroupKOps
import com.avast.sst.bundle.MonixServerApp
import com.avast.sst.http4s.client.Http4sBlazeClientModule
import com.avast.sst.http4s.server.Http4sBlazeServerModule
import com.avast.sst.pureconfig.PureConfigModule
import com.github.mattszm.panda.bootstrap.configuration.AppConfiguration
import com.github.mattszm.panda.db.MongoAppClient
import com.github.mattszm.panda.gateway.{ApiGatewayRouting, BaseApiGatewayImpl}
import com.github.mattszm.panda.management.ManagementRouting
import com.github.mattszm.panda.participant.{Participant, ParticipantsCacheImpl}
import com.github.mattszm.panda.routes.dto.RoutesMappingInitDto
import com.github.mattszm.panda.routes.{Group, RoutesTreeImpl}
import com.github.mattszm.panda.user._
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.http4s.server.{AuthMiddleware, Server}

import scala.io.Source

object App extends MonixServerApp {
  override def program: Resource[Task, Server] =
    for {
      appConfiguration <- Resource.eval(PureConfigModule.makeOrRaise[Task, AppConfiguration])
      mongoConnection = new MongoAppClient(appConfiguration.db).getConnection

      routesMappingConfiguration <- Resource.eval(Task.evalOnce(
        ujson.read(Source.fromResource(appConfiguration.gateway.mappingFile).mkString)))
      routesMappingInitializationEntries = RoutesMappingInitDto.of(routesMappingConfiguration)
      routesTree = RoutesTreeImpl.construct(routesMappingInitializationEntries)

      userDao = new UserDaoImpl(List(appConfiguration.initUser))(mongoConnection)

      httpGatewayClient <- Http4sBlazeClientModule.make[Task](appConfiguration.gatewayClient, global)
      tempParticipants = List(
        Participant("127.0.0.1", 3000, Group("cars")),
        Participant("localhost", 3001, Group("cars")),
        Participant("127.0.0.1", 4000, Group("planes"), "planesInstance1")
      ) // temp solution

      participantsCache <- Resource.eval(ParticipantsCacheImpl(tempParticipants))
      loadBalancer = appConfiguration.gateway.loadBalanceAlgorithm.create(
        client = httpGatewayClient,
        participantsCache = participantsCache
      )
      apiGateway = new BaseApiGatewayImpl(loadBalancer, routesTree)

      apiGatewayRouting = new ApiGatewayRouting(apiGateway)
      authRouting = new AuthRouting(userDao)
      managementRouting = new ManagementRouting(participantsCache)

      authenticator = new AuthenticatorBasedOnHeader(userDao)
      authMiddleware = AuthMiddleware(authenticator.authUser, authenticator.onFailure)
      managementAuthedService = authMiddleware(managementRouting.getRoutes)

      allRoutes = (apiGatewayRouting.getRoutes <+> authRouting.getRoutes <+> managementAuthedService).orNotFound
      server <- Http4sBlazeServerModule.make[Task](
        appConfiguration.appServer,
        allRoutes,
        global
      )
    } yield server
}
