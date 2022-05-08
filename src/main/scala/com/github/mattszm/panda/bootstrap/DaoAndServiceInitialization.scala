package com.github.mattszm.panda.bootstrap

import com.github.mattszm.panda.db.DbAppClient
import com.github.mattszm.panda.participant.event.{ParticipantEventDao, ParticipantEventDaoImpl, ParticipantEventService, ParticipantEventServiceImpl}
import com.github.mattszm.panda.sequence.SequenceDao
import com.github.mattszm.panda.user._

final class DaoAndServiceInitialization(
                                         private val dbAppClient: DbAppClient,
                                         private val initUsers: List[UserCredentials],
                                       ) {

  private val userDao: UserDao = new UserDaoImpl(dbAppClient.getConnection)
  private val userService: UserService = new UserServiceImpl(userDao, initUsers)(dbAppClient.getConnection)

  private val sequenceDao: SequenceDao = new SequenceDao(dbAppClient.getConnection)

  private val participantEventDao: ParticipantEventDao = new ParticipantEventDaoImpl(dbAppClient.getConnection)
  private val participantEventService: ParticipantEventService = new ParticipantEventServiceImpl(
    participantEventDao = participantEventDao,
    sequenceDao = sequenceDao
  )(dbAppClient.getConnection)


//  participantEventService.createParticipant(
//    ParticipantCreationDto(
//      "localhost",
//      2000,
//      "cars",
//      Some("Some random identifier6"),
//      Some("fdsafdasasdfasdfsdfasdafadsfdd"),
//      None
//    )
//  ).foreachL(l => println("result: " + l ))runSyncUnsafe(5.seconds)


  def getUserService: UserService = userService

  def getParticipantEventService: ParticipantEventService = participantEventService
}
