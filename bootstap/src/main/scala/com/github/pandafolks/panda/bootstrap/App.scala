package com.github.pandafolks.panda.bootstrap

import cats.effect.Resource
import cats.implicits.toSemigroupKOps
import com.avast.sst.bundle.MonixServerApp
import com.avast.sst.http4s.server.Http4sEmberServerModule
import com.avast.sst.pureconfig.PureConfigModule
import com.github.pandafolks.panda.bootstrap.configuration.AppConfiguration
import com.github.pandafolks.panda.bootstrap.init.{ModulesInitializedAfterCachesFulfilled, ModulesInitializedBeforeCachesFulfilled}
import com.github.pandafolks.panda.db.MongoAppClient
import com.github.pandafolks.panda.gateway.{ApiGatewayRouting, BaseApiGatewayImpl}
import com.github.pandafolks.panda.healthcheck.DistributedHealthCheckServiceImpl
import com.github.pandafolks.panda.httpClient.HttpClient
import com.github.pandafolks.panda.participant.{ParticipantsCacheImpl, ParticipantsRouting}
import com.github.pandafolks.panda.routes.RoutesRouting
import com.github.pandafolks.panda.user.AuthRouting
import com.github.pandafolks.panda.user.token.AuthenticatorBasedOnHeader
import monix.eval.Task
import org.http4s.server.{AuthMiddleware, Server}

object App extends MonixServerApp {
  override def program: Resource[Task, Server] =
    for {
      appConfiguration <- Resource.eval(PureConfigModule.makeOrRaise[Task, AppConfiguration])
      dbAppClient = new MongoAppClient(appConfiguration.db)

      modulesInitializedBeforeCaches = new ModulesInitializedBeforeCachesFulfilled(dbAppClient, appConfiguration)

      participantsCache <- Resource.eval(ParticipantsCacheImpl(
        modulesInitializedBeforeCaches.getParticipantEventService,
        modulesInitializedBeforeCaches.getBackgroundJobsRegistry,
        cacheRefreshIntervalInMillis = appConfiguration.consistency.getRealFullConsistencyMaxDelayInMillis
      )) // Loading participants cache as soon as possible because many other mechanisms are based on this cached content.
      treesService <- Resource.eval(modulesInitializedBeforeCaches.getTreesService) // treesService is some kind of cache.

      modulesInitializedAfterCaches = new ModulesInitializedAfterCachesFulfilled(dbAppClient, appConfiguration, modulesInitializedBeforeCaches.getBackgroundJobsRegistry)

      // Http clients
      httpGatewayClient <- HttpClient.createMonixBased(appConfiguration.gatewayClient)
      httpInternalClient <- HttpClient.createMonixBased(appConfiguration.internalClient)

      loadBalancer = appConfiguration.gateway.loadBalancerAlgorithm.create(
        client = httpGatewayClient,
        participantsCache = participantsCache,
        appConfiguration.gateway.loadBalancerRetries,
        backgroundJobsRegistry = Some(modulesInitializedBeforeCaches.getBackgroundJobsRegistry)
      )
      apiGateway = new BaseApiGatewayImpl(loadBalancer, treesService)

      _ = new DistributedHealthCheckServiceImpl(
        modulesInitializedBeforeCaches.getParticipantEventService,
        participantsCache,
        modulesInitializedAfterCaches.getNodeTrackerService,
        modulesInitializedBeforeCaches.getUnsuccessfulHealthCheckDao,
        httpInternalClient,
        modulesInitializedBeforeCaches.getBackgroundJobsRegistry
      )(appConfiguration.healthCheckConfig)

      // routing
      apiGatewayRouting = new ApiGatewayRouting(apiGateway)
      authRouting = new AuthRouting(modulesInitializedBeforeCaches.getTokenService, modulesInitializedBeforeCaches.getUserService)

      authenticator = new AuthenticatorBasedOnHeader(modulesInitializedBeforeCaches.getTokenService, modulesInitializedBeforeCaches.getUserService)(
        appConfiguration.consistency.getRealFullConsistencyMaxDelayInMillis)
      authMiddleware = AuthMiddleware(authenticator.authUser, authenticator.onFailure)
      participantsRouting = new ParticipantsRouting(modulesInitializedBeforeCaches.getParticipantEventService, participantsCache)
      routesRouting = new RoutesRouting(modulesInitializedBeforeCaches.getRoutesService)

      authedRoutes = authMiddleware(
        participantsRouting.getRoutesWithAuth
          <+> routesRouting.getRoutesWithAuth
          <+> authRouting.getRoutesWithAuth
      )

      allRoutes = (apiGatewayRouting.getRoutes <+> authRouting.getRoutes <+> authedRoutes).orNotFound

      // main server
      server <- Http4sEmberServerModule.make[Task](
        appConfiguration.appServer,
        allRoutes
      )
    } yield server
}
