package com.github.pandafolks.panda.bootstrap

import cats.effect.Resource
import cats.implicits.toSemigroupKOps
import com.avast.sst.bundle.MonixServerApp
import com.avast.sst.http4s.server.Http4sEmberServerModule
import com.avast.sst.pureconfig.PureConfigModule
import com.github.pandafolks.panda.backgroundjobsregistry.InMemoryBackgroundJobsRegistryImpl
import com.github.pandafolks.panda.bootstrap.configuration.AppConfiguration
import com.github.pandafolks.panda.bootstrap.init.{
  DaosAndServicesInitializedAfterCachesFulfilled,
  DaosAndServicesInitializedBeforeCachesFulfilled
}
import com.github.pandafolks.panda.db.MongoAppClient
import com.github.pandafolks.panda.gateway.{ApiGatewayRouting, BaseApiGatewayImpl}
import com.github.pandafolks.panda.healthcheck.DistributedHealthCheckServiceImpl
import com.github.pandafolks.panda.httpClient.HttpClient
import com.github.pandafolks.panda.participant.{ParticipantsCacheImpl, ParticipantsRouting}
import com.github.pandafolks.panda.routes.RoutesRouting
import com.github.pandafolks.panda.user.AuthRouting
import com.github.pandafolks.panda.user.token.AuthenticatorBasedOnHeader
import com.github.pandafolks.panda.utils.scheduler.CoreScheduler
import monix.eval.Task
import org.http4s.server.{AuthMiddleware, Server}

object App extends MonixServerApp {
  override def program: Resource[Task, Server] =
    for {
      appConfiguration <- Resource.eval(PureConfigModule.makeOrRaise[Task, AppConfiguration])
      dbAppClient = new MongoAppClient(appConfiguration.db)
      backgroundJobsRegistry = new InMemoryBackgroundJobsRegistryImpl(CoreScheduler.scheduler)

      daosAndServicesInitializedBeforeCaches = new DaosAndServicesInitializedBeforeCachesFulfilled(
        dbAppClient,
        appConfiguration,
        backgroundJobsRegistry
      )

      participantsCache <- Resource.eval(
        ParticipantsCacheImpl(
          daosAndServicesInitializedBeforeCaches.getParticipantEventService,
          backgroundJobsRegistry,
          cacheRefreshIntervalInMillis = appConfiguration.consistency.getRealFullConsistencyMaxDelayInMillis
        )
      ) // Loading participants cache as soon as possible because many other mechanisms are based on this cached content.
      treesService <- Resource.eval(
        daosAndServicesInitializedBeforeCaches.getTreesService
      ) // treesService is some kind of cache.

      daosAndServicesInitializedAfterCaches = new DaosAndServicesInitializedAfterCachesFulfilled(
        dbAppClient,
        appConfiguration,
        backgroundJobsRegistry
      )

      // Http clients
      httpGatewayClient <- HttpClient.createMonixBased(appConfiguration.gatewayClient)
      httpInternalClient <- HttpClient.createMonixBased(appConfiguration.internalClient)

      loadBalancer = appConfiguration.gateway.loadBalancerAlgorithm.create(
        client = httpGatewayClient,
        participantsCache = participantsCache,
        appConfiguration.gateway.loadBalancerRetries,
        backgroundJobsRegistry = Some(backgroundJobsRegistry)
      )
      apiGateway = new BaseApiGatewayImpl(loadBalancer, treesService)

      _ = new DistributedHealthCheckServiceImpl(
        daosAndServicesInitializedBeforeCaches.getParticipantEventService,
        participantsCache,
        daosAndServicesInitializedAfterCaches.getNodeTrackerService,
        daosAndServicesInitializedBeforeCaches.getUnsuccessfulHealthCheckDao,
        httpInternalClient,
        backgroundJobsRegistry
      )(appConfiguration.healthCheckConfig)

      // routing
      apiGatewayRouting = new ApiGatewayRouting(apiGateway)
      authRouting = new AuthRouting(
        daosAndServicesInitializedBeforeCaches.getTokenService,
        daosAndServicesInitializedBeforeCaches.getUserService
      )

      authenticator = new AuthenticatorBasedOnHeader(
        daosAndServicesInitializedBeforeCaches.getTokenService,
        daosAndServicesInitializedBeforeCaches.getUserService
      )(appConfiguration.consistency.getRealFullConsistencyMaxDelayInMillis)
      authMiddleware = AuthMiddleware(authenticator.authUser, authenticator.onFailure)
      participantsRouting = new ParticipantsRouting(
        daosAndServicesInitializedBeforeCaches.getParticipantEventService,
        participantsCache
      )
      routesRouting = new RoutesRouting(daosAndServicesInitializedBeforeCaches.getRoutesService)

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
