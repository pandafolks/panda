package com.github.mattszm.panda.bootstrap

import com.github.mattszm.panda.bootstrap.configuration.AppConfiguration
import com.github.mattszm.panda.db.DbAppClient
import com.github.mattszm.panda.participant.event.{ParticipantEventDao, ParticipantEventDaoImpl, ParticipantEventService, ParticipantEventServiceImpl}
import com.github.mattszm.panda.sequence.SequenceDao
import com.github.mattszm.panda.user.token.{TokenService, TokenServiceImpl}
import com.github.mattszm.panda.user.{UserDao, UserDaoImpl, UserService, UserServiceImpl}

final class DaoAndServiceInitialization(
                                         private val dbAppClient: DbAppClient,
                                         private val appConfiguration: AppConfiguration,
                                       ) {

  private val userDao: UserDao = new UserDaoImpl(dbAppClient.getUsersConnection)
  private val userService: UserService = new UserServiceImpl(userDao, List(appConfiguration.initUser))(dbAppClient.getUsersConnection)

  private val sequenceDao: SequenceDao = new SequenceDao()

  private val participantEventDao: ParticipantEventDao = new ParticipantEventDaoImpl(dbAppClient.getParticipantEventsAndSequencesConnection)
  private val participantEventService: ParticipantEventService = new ParticipantEventServiceImpl(
    participantEventDao = participantEventDao,
    sequenceDao = sequenceDao
  )(dbAppClient.getParticipantEventsAndSequencesConnection)

  private val tokenService: TokenService = new TokenServiceImpl(appConfiguration.authTokens, dbAppClient.getTokensConnection)

  def getUserService: UserService = userService

  def getParticipantEventService: ParticipantEventService = participantEventService

  def getTokenService: TokenService = tokenService
}
