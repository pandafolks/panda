package com.github.mattszm.panda.bootstrap

import com.github.mattszm.panda.configuration.AppConfiguration
import com.github.mattszm.panda.db.DbAppClient
import com.github.mattszm.panda.participant.event.{ParticipantEventDao, ParticipantEventDaoImpl, ParticipantEventService, ParticipantEventServiceImpl}
import com.github.mattszm.panda.sequence.SequenceDao
import com.github.mattszm.panda.user._
import com.github.mattszm.panda.user.token.{TokenService, TokenServiceImpl}

final class DaoAndServiceInitialization(
                                         private val dbAppClient: DbAppClient,
                                         private val appConfiguration: AppConfiguration,
                                       ) {

  private val userDao: UserDao = new UserDaoImpl(dbAppClient.getConnection)
  private val userService: UserService = new UserServiceImpl(userDao, List(appConfiguration.initUser))(dbAppClient.getConnection)

  private val sequenceDao: SequenceDao = new SequenceDao(dbAppClient.getConnection)

  private val participantEventDao: ParticipantEventDao = new ParticipantEventDaoImpl(dbAppClient.getConnection)
  private val participantEventService: ParticipantEventService = new ParticipantEventServiceImpl(
    participantEventDao = participantEventDao,
    sequenceDao = sequenceDao
  )(dbAppClient.getConnection)

  private val tokenService: TokenService = new TokenServiceImpl(appConfiguration.authTokens, dbAppClient.getConnection)

  def getUserService: UserService = userService

  def getParticipantEventService: ParticipantEventService = participantEventService

  def getTokenService: TokenService = tokenService
}
