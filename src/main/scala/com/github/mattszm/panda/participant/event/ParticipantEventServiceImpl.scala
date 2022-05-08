package com.github.mattszm.panda.participant.event

import cats.data.EitherT
import cats.effect.Resource
import com.github.mattszm.panda.participant.Participant
import com.github.mattszm.panda.participant.Participant.HEARTBEAT_DEFAULT_ROUTE
import com.github.mattszm.panda.participant.dto.ParticipantCreationDto
import com.github.mattszm.panda.sequence.{Sequence, SequenceDao, SequenceKey}
import com.github.mattszm.panda.user.User
import com.github.mattszm.panda.utils.{AlreadyExists, PersistenceError}
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task

final class ParticipantEventServiceImpl(
                                         private val participantEventDao: ParticipantEventDao,
                                         private val sequenceDao: SequenceDao)(
                                         private val c: Resource[Task, (
                                           CollectionOperator[User],
                                             CollectionOperator[ParticipantEvent],
                                             CollectionOperator[Sequence]
                                           )]) extends ParticipantEventService {

  override def createParticipant(participantCreationDto: ParticipantCreationDto): Task[Either[PersistenceError, Unit]] = {
    val initParticipantDataModification = ParticipantEventDataModification(
      host = Some(participantCreationDto.host),
      port = Some(participantCreationDto.port),
      groupName = Some(participantCreationDto.groupName),
      heartbeatRoute = participantCreationDto.heartbeatRoute.orElse(Some(HEARTBEAT_DEFAULT_ROUTE))
    )

    val participantIdentifier = participantCreationDto.identifier.getOrElse(
      Participant.createDefaultIdentifier(
        participantCreationDto.host,
        participantCreationDto.port,
        participantCreationDto.groupName
      )
    )

    c.use {
      case (_, participantEventOperator, sequenceOperator) =>
        for {
          exists <- participantEventDao.exists(participantIdentifier, participantEventOperator)

          initRes <- if (exists)
            Task.now(Left(AlreadyExists("Participant with identifier \"" + participantIdentifier + "\" already exists")))
          else
            insertEvent(
              participantIdentifier,
              initParticipantDataModification,
              EventType.Created()
            )(sequenceOperator, participantEventOperator)

          finalRes <-
            if (initRes.isLeft) Task.now(initRes)
            else if (!participantCreationDto.working.getOrElse(true)) Task.now(Right(()))
            else
              insertEvent(
                participantIdentifier,
                ParticipantEventDataModification.empty,
                EventType.Joined()
              )(sequenceOperator, participantEventOperator)
        } yield finalRes
    }
  }

  private def insertEvent(
                           participantIdentifier: String,
                           participantDataModification: ParticipantEventDataModification,
                           eventType: EventType
                         )(
    seqOperator: CollectionOperator[Sequence], participantEventOperator: CollectionOperator[ParticipantEvent]
  ): Task[Either[PersistenceError, Unit]] =
    EitherT(sequenceDao.getNextSequence(SequenceKey.getParticipantEventSequence, seqOperator))
      .flatMap(seq => EitherT(
        participantEventDao.insertOne(
          ParticipantEvent(
            participantIdentifier = participantIdentifier,
            participantDataModification = participantDataModification,
            eventId = seq,
            eventType = eventType,
          ),
          participantEventOperator,
        )
      )).value
}
