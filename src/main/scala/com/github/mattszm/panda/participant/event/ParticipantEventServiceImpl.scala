package com.github.mattszm.panda.participant.event

import cats.data.EitherT
import cats.effect.Resource
import com.github.mattszm.panda.participant.Participant.HEARTBEAT_DEFAULT_ROUTE
import com.github.mattszm.panda.participant.dto.ParticipantModificationDto
import com.github.mattszm.panda.sequence.{Sequence, SequenceDao, SequenceKey}
import com.github.mattszm.panda.user.User
import com.github.mattszm.panda.user.token.Token
import com.github.mattszm.panda.utils.{AlreadyExists, NotExists, PersistenceError, UnsuccessfulSaveOperation}
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task

final class ParticipantEventServiceImpl(
                                         private val participantEventDao: ParticipantEventDao,
                                         private val sequenceDao: SequenceDao)(
                                         private val c: Resource[Task, (CollectionOperator[User],
                                           CollectionOperator[ParticipantEvent], CollectionOperator[Sequence],
                                           CollectionOperator[Token])]) extends ParticipantEventService {

  private val identifierCannotBeBlankError: Task[Left[UnsuccessfulSaveOperation, Nothing]] = Task.evalOnce(Left(UnsuccessfulSaveOperation("Identifier cannot be blank")))

  override def createParticipant(participantModificationDto: ParticipantModificationDto): Task[Either[PersistenceError, String]] = {
    if (participantModificationDto.host.isEmpty || participantModificationDto.port.isEmpty || participantModificationDto.groupName.isEmpty)
      return Task.now(Left(UnsuccessfulSaveOperation("host, port and groupName have to be defined")))

    val participantIdentifier = participantModificationDto.getIdentifier
    if (participantIdentifier.isEmpty || (participantIdentifier.isDefined && participantIdentifier.getOrElse("").isBlank))
      return identifierCannotBeBlankError

    c.use {
      case (_, participantEventOperator, sequenceOperator, _) =>
        for {
          exists <- participantEventDao.exists(participantIdentifier.get, participantEventOperator)

          initRes <- if (exists)
            Task.now(Left(AlreadyExists("Participant with identifier \"" + participantIdentifier.get + "\" already exists")))
          else
            insertEvent(
              participantIdentifier.get,
              ParticipantEventDataModification.of(participantModificationDto)
                .copy(heartbeatRoute = participantModificationDto.heartbeatRoute.orElse(Some(HEARTBEAT_DEFAULT_ROUTE))),
              ParticipantEventType.Created()
            )(sequenceOperator, participantEventOperator)

          finalRes <-
            if (initRes.isLeft || !participantModificationDto.working.getOrElse(true)) Task.now(initRes)
            else
              insertEvent(
                participantIdentifier.get,
                ParticipantEventDataModification.empty,
                ParticipantEventType.TurnedOn()
              )(sequenceOperator, participantEventOperator)
        } yield finalRes
    }
  }

  override def modifyParticipant(participantModificationDto: ParticipantModificationDto): Task[Either[PersistenceError, String]] = {
    val participantIdentifier = participantModificationDto.getIdentifier
    if (participantIdentifier.isEmpty || (participantIdentifier.isDefined && participantIdentifier.getOrElse("").isBlank))
      return identifierCannotBeBlankError

    c.use {
      case (_, participantEventOperator, sequenceOperator, _) =>
        for {
          exists <- participantEventDao.exists(participantIdentifier.get, participantEventOperator)

          initRes <- if (!exists)
            Task.now(Left(NotExists("Participant with identifier \"" + participantIdentifier.get + "\" does not exist")))
          else
            insertEvent(
              participantIdentifier.get,
              ParticipantEventDataModification.of(participantModificationDto)
                .copy(heartbeatRoute = participantModificationDto.heartbeatRoute),
              ParticipantEventType.ModifiedData()
            )(sequenceOperator, participantEventOperator)

          finalRes <-
            if (initRes.isLeft) Task.now(initRes)
            else if (!participantModificationDto.working.getOrElse(true))
              insertEvent(
                participantIdentifier.get,
                ParticipantEventDataModification.empty,
                ParticipantEventType.TurnedOff()
              )(sequenceOperator, participantEventOperator)
            else
              insertEvent(
                participantIdentifier.get,
                ParticipantEventDataModification.empty,
                ParticipantEventType.TurnedOn()
              )(sequenceOperator, participantEventOperator)
        } yield finalRes
    }
  }


  override def removeParticipant(participantIdentifier: String): Task[Either[PersistenceError, String]] = {
    if (participantIdentifier.isBlank) return identifierCannotBeBlankError

    c.use {
      case (_, participantEventOperator, sequenceOperator, _) =>
        for {
          exists <- participantEventDao.exists(participantIdentifier, participantEventOperator)

          res <- if (!exists)
            Task.now(Left(NotExists("Participant with identifier \"" + participantIdentifier + "\" does not exist")))
          else
            insertEvent(
              participantIdentifier,
              ParticipantEventDataModification.empty,
              ParticipantEventType.Removed()
            )(sequenceOperator, participantEventOperator)
        } yield res
    }
  }

  private def insertEvent(
                           participantIdentifier: String,
                           participantDataModification: ParticipantEventDataModification,
                           eventType: ParticipantEventType
                         )(
    seqOperator: CollectionOperator[Sequence], participantEventOperator: CollectionOperator[ParticipantEvent]
  ): Task[Either[PersistenceError, String]] =
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
      )).map(_ => participantIdentifier).value
}
