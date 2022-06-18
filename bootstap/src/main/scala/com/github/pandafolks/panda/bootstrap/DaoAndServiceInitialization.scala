package com.github.pandafolks.panda.bootstrap

import com.github.pandafolks.panda.bootstrap.configuration.AppConfiguration
import com.github.pandafolks.panda.db.DbAppClient
import com.github.pandafolks.panda.participant.event.{ParticipantEventDao, ParticipantEventDaoImpl, ParticipantEventService, ParticipantEventServiceImpl}
import com.github.pandafolks.panda.user.token.{TokenService, TokenServiceImpl}
import com.github.pandafolks.panda.user.{UserDao, UserDaoImpl, UserService, UserServiceImpl}
import com.pandafolks.mattszm.panda.sequence.SequenceDao

final class DaoAndServiceInitialization(
                                         private val dbAppClient: DbAppClient,
                                         private val appConfiguration: AppConfiguration,
                                       ) {

  private val userDao: UserDao = new UserDaoImpl(dbAppClient.getUsersWithTokensConnection)
  private val userService: UserService = new UserServiceImpl(userDao, List(appConfiguration.initUser))(dbAppClient.getUsersWithTokensConnection)

  private val sequenceDao: SequenceDao = new SequenceDao()

  private val participantEventDao: ParticipantEventDao = new ParticipantEventDaoImpl(dbAppClient.getParticipantEventsAndSequencesConnection)
  private val participantEventService: ParticipantEventService = new ParticipantEventServiceImpl(
    participantEventDao = participantEventDao,
    sequenceDao = sequenceDao
  )(dbAppClient.getParticipantEventsAndSequencesConnection)

  private val tokenService: TokenService = new TokenServiceImpl(appConfiguration.authTokens)(dbAppClient.getUsersWithTokensConnection)

  def getUserService: UserService = userService

  def getParticipantEventService: ParticipantEventService = participantEventService

  def getTokenService: TokenService = tokenService
}
