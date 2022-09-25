package com.github.pandafolks.panda.bootstrap.init

import com.github.pandafolks.panda.backgroundjobsregistry
import com.github.pandafolks.panda.backgroundjobsregistry.BackgroundJobsRegistry
import com.github.pandafolks.panda.bootstrap.configuration.AppConfiguration
import com.github.pandafolks.panda.db.DbAppClient
import com.github.pandafolks.panda.healthcheck.{UnsuccessfulHealthCheckDao, UnsuccessfulHealthCheckDaoImpl}
import com.github.pandafolks.panda.participant.event.{ParticipantEventDao, ParticipantEventDaoImpl, ParticipantEventService, ParticipantEventServiceImpl}
import com.github.pandafolks.panda.routes.{MapperDao, MapperDaoImpl, PrefixDao, PrefixDaoImpl, RoutesService, RoutesServiceImpl, TreesService, TreesServiceImpl}
import com.github.pandafolks.panda.user.token.{TokenService, TokenServiceImpl}
import com.github.pandafolks.panda.user.{UserDao, UserDaoImpl, UserService, UserServiceImpl}
import com.pandafolks.mattszm.panda.sequence
import com.pandafolks.mattszm.panda.sequence.SequenceDao
import monix.eval.Task

/**
 * These Modules can be initialized at any point in time. Rule of thumb -> the faster the better.
 */
final class ModulesInitializedBeforeCachesFulfilled(
                                                     private val dbAppClient: DbAppClient,
                                                     private val appConfiguration: AppConfiguration,
                                                   ) extends ModulesInitialization {
  locally {

    backgroundjobsregistry.launch()

    sequence.launch(
      dbName = dbAppClient.getDbName
    )()

  }

  private val sequenceDao: SequenceDao = sequence.getSequenceDao

  private val participantEventDao: ParticipantEventDao = new ParticipantEventDaoImpl(dbAppClient.getParticipantEventsAndSequencesConnection)
  private val participantEventService: ParticipantEventService = new ParticipantEventServiceImpl(
    participantEventDao = participantEventDao,
    sequenceDao = sequenceDao
  )(dbAppClient.getParticipantEventsAndSequencesConnection)

  private val userDao: UserDao = new UserDaoImpl(dbAppClient.getUsersWithTokensConnection)
  private val userService: UserService = new UserServiceImpl(userDao, List(appConfiguration.initUser))(dbAppClient.getUsersWithTokensConnection)

  private val tokenService: TokenService = new TokenServiceImpl(appConfiguration.authTokens)(dbAppClient.getUsersWithTokensConnection)

  private val unsuccessfulHealthCheckDao: UnsuccessfulHealthCheckDao = new UnsuccessfulHealthCheckDaoImpl(dbAppClient.getUnsuccessfulHealthCheckConnection)

  private val mapperDao: MapperDao = new MapperDaoImpl()
  private val prefixDao: PrefixDao = new PrefixDaoImpl()
  private val routesService: RoutesService = new RoutesServiceImpl(mapperDao, prefixDao)(dbAppClient.getMappersAndPrefixesConnection)(appConfiguration.consistency.getRealFullConsistencyMaxDelayInMillis)
  private val treesService: Task[TreesService] = TreesServiceImpl(mapperDao, prefixDao, getBackgroundJobsRegistry)(dbAppClient.getMappersAndPrefixesConnection)(appConfiguration.consistency.getRealFullConsistencyMaxDelayInMillis).uncancelable.memoize

  def getUserService: UserService = userService

  def getTokenService: TokenService = tokenService

  def getParticipantEventService: ParticipantEventService = participantEventService

  def getUnsuccessfulHealthCheckDao: UnsuccessfulHealthCheckDao = unsuccessfulHealthCheckDao

  def getRoutesService: RoutesService = routesService

  def getTreesService: Task[TreesService] = treesService

  def getBackgroundJobsRegistry: BackgroundJobsRegistry = backgroundjobsregistry.getBackgroundJobsRegistry
}
