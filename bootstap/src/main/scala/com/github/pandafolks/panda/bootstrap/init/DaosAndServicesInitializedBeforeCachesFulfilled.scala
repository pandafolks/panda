package com.github.pandafolks.panda.bootstrap.init

import com.github.pandafolks.panda.bootstrap.configuration.AppConfiguration
import com.github.pandafolks.panda.db.DbAppClient
import com.github.pandafolks.panda.healthcheck.{UnsuccessfulHealthCheckDao, UnsuccessfulHealthCheckDaoImpl}
import com.github.pandafolks.panda.participant.event.{ParticipantEventDao, ParticipantEventDaoImpl, ParticipantEventService, ParticipantEventServiceImpl}
import com.github.pandafolks.panda.routes.{PrefixesDao, PrefixesDaoImpl, MapperDao, MapperDaoImpl, RoutesService, RoutesServiceImpl}
import com.github.pandafolks.panda.user.token.{TokenService, TokenServiceImpl}
import com.github.pandafolks.panda.user.{UserDao, UserDaoImpl, UserService, UserServiceImpl}
import com.pandafolks.mattszm.panda.sequence.SequenceDao

/**
 * These Daos and Services can be initialized at any point in time. Rule of thumb -> the faster the better.
 */
final class DaosAndServicesInitializedBeforeCachesFulfilled(
                                                            private val dbAppClient: DbAppClient,
                                                            private val appConfiguration: AppConfiguration,
                                                          ) extends DaosAndServicesInitialization {

  private val sequenceDao: SequenceDao = new SequenceDao()

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
  private val prefixesDao: PrefixesDao = new PrefixesDaoImpl()
  private val routesService: RoutesService = new RoutesServiceImpl(mapperDao, prefixesDao)(dbAppClient.getMappersAndPrefixesConnection)(appConfiguration.consistency.getRealFullConsistencyMaxDelayInMillis)

  def getUserService: UserService = userService

  def getTokenService: TokenService = tokenService

  def getParticipantEventService: ParticipantEventService = participantEventService

  def getUnsuccessfulHealthCheckDao: UnsuccessfulHealthCheckDao = unsuccessfulHealthCheckDao

  def getRoutesService: RoutesService = routesService

}
