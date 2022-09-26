package com.github.pandafolks.panda

import cats.effect.Resource
import com.github.pandafolks.panda.backgroundjobsregistry.BackgroundJobsRegistry
import com.github.pandafolks.panda.participant.event.ParticipantEvent.PARTICIPANT_EVENTS_COLLECTION_NAME
import com.github.pandafolks.panda.participant.event.{ParticipantEvent, ParticipantEventDao, ParticipantEventDaoImpl, ParticipantEventService, ParticipantEventServiceImpl}
import com.github.pandafolks.panda.utils.PandaStartupException
import com.pandafolks.mattszm.panda.sequence.{Sequence, SequenceDao}
import monix.connect.mongodb.client.{CollectionCodecRef, MongoConnection}
import monix.eval.Task
import org.mongodb.scala.MongoClientSettings
import org.slf4j.LoggerFactory

package object participant {

  private val logger = LoggerFactory.getLogger(getClass.getName)

  private var cacheRefreshIntervalInMillisValue: Option[Int] = Option.empty
  private var backgroundJobsRegistryInstance: Option[BackgroundJobsRegistry] = Option.empty
  private var participantEventDao: Option[ParticipantEventDao] = Option.empty
  private var participantEventService: Option[ParticipantEventService] = Option.empty

  def launch(
              backgroundJobsRegistry: BackgroundJobsRegistry,
              sequenceDao: SequenceDao,
              settings: MongoClientSettings,
              dbName: String,
            )(
              cacheRefreshIntervalInMillis: Int
            )(
              sequenceCol: CollectionCodecRef[Sequence],
              participantEventCollectionName: String = PARTICIPANT_EVENTS_COLLECTION_NAME

            ): Unit = {
    logger.info("Creating \'participant\' module...")

    val participantEventsCol: CollectionCodecRef[ParticipantEvent] = ParticipantEvent.getCollection(dbName, participantEventCollectionName)
    val participantEventsAndSequencesConnection = MongoConnection.create2(settings, (participantEventsCol, sequenceCol))

    cacheRefreshIntervalInMillisValue = Some(cacheRefreshIntervalInMillis)
    backgroundJobsRegistryInstance = Some(backgroundJobsRegistry)
    participantEventDao = Some(new ParticipantEventDaoImpl(participantEventsAndSequencesConnection))
    participantEventService = Some(new ParticipantEventServiceImpl(
      participantEventDao = participantEventDao.get,
      sequenceDao = sequenceDao
    )(participantEventsAndSequencesConnection))

    logger.info("\'participant\' module created successfully")
  }

  def getParticipantEventService: ParticipantEventService =
    try {
      participantEventService.get
    } catch {
      case _: NoSuchElementException =>
        logger.error("ParticipantEventService not initialized - launch the \'participant\' module firstly")
        throw new PandaStartupException("\'participant\' module is not initialized properly")
    }

  def createParticipantsCache: Resource[Task, ParticipantsCache] =
    Resource.eval(ParticipantsCacheImpl(
      participantEventService = participantEventService.get,
      backgroundJobsRegistry = backgroundJobsRegistryInstance.get,
      cacheRefreshIntervalInMillis = cacheRefreshIntervalInMillisValue.get
    ))

}
