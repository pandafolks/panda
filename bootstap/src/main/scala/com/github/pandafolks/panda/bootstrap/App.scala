package com.github.pandafolks.panda.bootstrap

import cats.effect.Resource
import cats.implicits.toSemigroupKOps
import com.avast.sst.bundle.MonixServerApp
import com.avast.sst.http4s.client.Http4sBlazeClientModule
import com.avast.sst.http4s.server.Http4sBlazeServerModule
import com.avast.sst.pureconfig.PureConfigModule
import com.github.pandafolks.panda.bootstrap.configuration.AppConfiguration
import com.github.pandafolks.panda.db.MongoAppClient
import com.github.pandafolks.panda.gateway.{ApiGatewayRouting, BaseApiGatewayImpl}
import com.github.pandafolks.panda.healthcheck.DistributedHealthCheckServiceImpl
import com.github.pandafolks.panda.participant.{ParticipantsCacheImpl, ParticipantsRouting}
import com.github.pandafolks.panda.routes.RoutesTrees
import com.github.pandafolks.panda.routes.dto.RoutesMappingInitDto
import com.github.pandafolks.panda.user.AuthRouting
import com.github.pandafolks.panda.user.token.AuthenticatorBasedOnHeader
import monix.eval.Task
import monix.execution.Scheduler.global
import org.http4s.server.{AuthMiddleware, Server}

import scala.io.Source

object App extends MonixServerApp {
  override def program: Resource[Task, Server] =
    for {
      appConfiguration <- Resource.eval(PureConfigModule.makeOrRaise[Task, AppConfiguration])

      dbAppClient = new MongoAppClient(appConfiguration.db)
      daosAndServices = new DaoAndServiceInitialization(dbAppClient, appConfiguration)

      routesMappingConfiguration <- Resource.eval(Task.evalOnce(
        ujson.read(Source.fromResource(appConfiguration.gateway.mappingFile).mkString)))
      routesMappingInitializationEntries = RoutesMappingInitDto.of(routesMappingConfiguration)
      routesTrees = RoutesTrees.construct(routesMappingInitializationEntries)

      httpGatewayClient <- Http4sBlazeClientModule.make[Task](appConfiguration.gatewayClient, global)

      participantsCache <- Resource.eval(ParticipantsCacheImpl(
        daosAndServices.getParticipantEventService,
        cacheRefreshInterval = appConfiguration.consistency.fullConsistencyMaxDelay
      ))
      loadBalancer = appConfiguration.gateway.loadBalanceAlgorithm.create(
        client = httpGatewayClient,
        participantsCache = participantsCache
      )
      apiGateway = new BaseApiGatewayImpl(loadBalancer, routesTrees)
      _ = new DistributedHealthCheckServiceImpl(
        daosAndServices.getParticipantEventService, participantsCache)(appConfiguration.healthCheckConfig)

      apiGatewayRouting = new ApiGatewayRouting(apiGateway)
      authRouting = new AuthRouting(daosAndServices.getTokenService, daosAndServices.getUserService)

      authenticator = new AuthenticatorBasedOnHeader(daosAndServices.getTokenService, daosAndServices.getUserService)(
        appConfiguration.consistency.fullConsistencyMaxDelay)
      authMiddleware = AuthMiddleware(authenticator.authUser, authenticator.onFailure)
      participantsRouting = new ParticipantsRouting(daosAndServices.getParticipantEventService, participantsCache)
      authedRoutes = authMiddleware(participantsRouting.getRoutesWithAuth <+> authRouting.getRoutesWithAuth)

      allRoutes = (apiGatewayRouting.getRoutes <+> authRouting.getRoutes <+> authedRoutes).orNotFound
      server <- Http4sBlazeServerModule.make[Task](
        appConfiguration.appServer,
        allRoutes,
        global
      )
    } yield server
}
