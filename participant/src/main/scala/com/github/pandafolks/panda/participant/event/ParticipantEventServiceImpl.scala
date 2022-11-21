package com.github.pandafolks.panda.participant.event

import cats.data.EitherT
import cats.effect.Resource
import com.github.pandafolks.panda.participant.Participant.HEALTHCHECK_DEFAULT_ROUTE
import com.github.pandafolks.panda.participant.{
  HealthcheckInfo,
  Healthy,
  NotWorking,
  Participant,
  ParticipantHealth,
  ParticipantModificationPayload,
  Unhealthy
}
import com.github.pandafolks.panda.routes.Group
import com.github.pandafolks.panda.utils.{AlreadyExists, NotExists, PersistenceError, UnsuccessfulSaveOperation}
import com.pandafolks.mattszm.panda.sequence.{Sequence, SequenceDao, SequenceKey}
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task
import monix.execution.atomic.AtomicLong

final class ParticipantEventServiceImpl(
    private val participantEventDao: ParticipantEventDao,
    private val sequenceDao: SequenceDao
)(private val c: Resource[Task, (CollectionOperator[ParticipantEvent], CollectionOperator[Sequence])])
    extends ParticipantEventService {

  private val identifierCannotBeBlankError: Task[Left[UnsuccessfulSaveOperation, Nothing]] =
    Task.evalOnce(Left(UnsuccessfulSaveOperation("Identifier cannot be blank")))

  private val participantEventSequenceKey: SequenceKey = SequenceKey("participantEventSequence")
  override def createParticipant(
      participantModificationPayload: ParticipantModificationPayload
  ): Task[Either[PersistenceError, String]] = {
    if (
      participantModificationPayload.host.isEmpty || participantModificationPayload.port.isEmpty || participantModificationPayload.groupName.isEmpty
    )
      return Task.now(Left(UnsuccessfulSaveOperation("host, port and groupName have to be defined")))

    val participantIdentifier = participantModificationPayload.getIdentifier
    if (
      participantIdentifier.isEmpty || (participantIdentifier.isDefined && participantIdentifier.getOrElse("").isBlank)
    )
      return identifierCannotBeBlankError

    c.use { case (participantEventOperator, sequenceOperator) =>
      for {
        exists <- participantEventDao.exists(participantIdentifier.get, participantEventOperator)

        initRes <- exists match {
          case Right(true) =>
            Task.now(
              Left(AlreadyExists("Participant with identifier \"" + participantIdentifier.get + "\" already exists"))
            )
          case Right(false) =>
            insertEvent(
              participantIdentifier.get,
              ParticipantEventDataModification
                .of(participantModificationPayload)
                .copy(healthcheckRoute =
                  participantModificationPayload.healthcheckRoute.orElse(Some(HEALTHCHECK_DEFAULT_ROUTE))
                ),
              ParticipantEventType.Created()
            )(sequenceOperator, participantEventOperator)
          case Left(value) => Task.now(Left(value))
        }

        finalRes <-
          if (initRes.isLeft || !participantModificationPayload.working.getOrElse(true)) Task.now(initRes)
          else
            insertEvent(
              participantIdentifier.get,
              ParticipantEventDataModification.empty,
              ParticipantEventType.TurnedOn()
            )(sequenceOperator, participantEventOperator)
      } yield finalRes
    }
  }

  override def modifyParticipant(
      participantModificationPayload: ParticipantModificationPayload
  ): Task[Either[PersistenceError, String]] = {
    val participantIdentifier = participantModificationPayload.getIdentifier
    if (
      participantIdentifier.isEmpty || (participantIdentifier.isDefined && participantIdentifier.getOrElse("").isBlank)
    )
      return identifierCannotBeBlankError

    c.use { case (participantEventOperator, sequenceOperator) =>
      for {
        exists <- participantEventDao.exists(participantIdentifier.get, participantEventOperator)

        initRes <- exists match {
          case Right(false) =>
            Task.now(
              Left(NotExists("Participant with identifier \"" + participantIdentifier.get + "\" does not exist"))
            )
          case Right(true) =>
            insertEvent(
              participantIdentifier.get,
              ParticipantEventDataModification.of(participantModificationPayload),
              ParticipantEventType.ModifiedData()
            )(sequenceOperator, participantEventOperator)
          case Left(value) => Task.now(Left(value))
        }

        finalRes <-
          if (initRes.isLeft || participantModificationPayload.working.isEmpty) Task.now(initRes)
          else
            insertEvent(
              participantIdentifier.get,
              ParticipantEventDataModification.empty,
              if (participantModificationPayload.working.get) ParticipantEventType.TurnedOn()
              else ParticipantEventType.TurnedOff()
            )(sequenceOperator, participantEventOperator)
      } yield finalRes
    }
  }

  override def removeParticipant(participantIdentifier: String): Task[Either[PersistenceError, String]] = {
    if (participantIdentifier.isBlank) return identifierCannotBeBlankError

    c.use { case (participantEventOperator, sequenceOperator) =>
      for {
        exists <- participantEventDao.exists(participantIdentifier, participantEventOperator)

        res <- exists match {
          case Right(false) =>
            Task.now(Left(NotExists("Participant with identifier \"" + participantIdentifier + "\" does not exist")))
          case Right(true) =>
            insertEvent(
              participantIdentifier,
              ParticipantEventDataModification.empty,
              ParticipantEventType.Removed()
            )(sequenceOperator, participantEventOperator)
          case Left(value) => Task.now(Left(value))
        }
      } yield res
    }
  }

  override def constructAllParticipants(): Task[(List[Participant], Long)] = {
    val dumbParticipant: Participant = Participant(
      host = "",
      port = -1,
      group = Group(""),
      identifier = "",
      healthcheckInfo = HealthcheckInfo(""),
      status = NotWorking
    )
    val lastSeenEventId: AtomicLong = AtomicLong(-1)

    c.use { case (participantEventOperator, _) =>
      participantEventDao
        .getOrderedEvents(participantEventOperator)
        .groupBy(_.participantIdentifier)
        .mergeMap { participantEventsGroup =>
          participantEventsGroup
            .foldLeft((dumbParticipant, false)) {
              case ((participant: Participant, shouldBeSkipped: Boolean), participantEvent: ParticipantEvent) =>
                lastSeenEventId.transform(_.max(participantEvent.eventId.getValue))
                participantEvent.convertEventIntoParticipant(participant, shouldBeSkipped)
            }
            .filterNot(_._2) // Do not return participants if there was a Removed event emitted and there was no Created event after it.
            .map(_._1)
        }
        .toListL
    }.map((_, lastSeenEventId.get()))
  }

  override def markParticipantAsHealthy(participantIdentifier: String): Task[Either[PersistenceError, Unit]] =
    markParticipantHealth(participantIdentifier, Healthy)

  override def markParticipantAsUnhealthy(participantIdentifier: String): Task[Either[PersistenceError, Unit]] =
    markParticipantHealth(participantIdentifier, Unhealthy)

  override def checkIfThereAreNewerEvents(eventId: Long): Task[Boolean] =
    participantEventDao.checkIfThereAreNewerEvents(eventId)

  private def markParticipantHealth(
      participantIdentifier: String,
      health: ParticipantHealth
  ): Task[Either[PersistenceError, Unit]] =
    c.use { case (participantEventOperator, sequenceOperator) =>
      for {
        exists <- participantEventDao.exists(participantIdentifier, participantEventOperator)

        res <- exists match {
          case Right(false) =>
            Task.now(Left(NotExists("Participant with identifier \"" + participantIdentifier + "\" does not exists")))
          case Right(true) =>
            insertEvent(
              participantIdentifier,
              ParticipantEventDataModification.empty,
              if (health == Healthy) ParticipantEventType.Joined() else ParticipantEventType.Disconnected()
            )(sequenceOperator, participantEventOperator).map(_.map(_ => ()))
          case Left(value) => Task.now(Left(value))
        }
      } yield res
    }

  private def insertEvent(
      participantIdentifier: String,
      participantDataModification: ParticipantEventDataModification,
      eventType: ParticipantEventType
  )(
      seqOperator: CollectionOperator[Sequence],
      participantEventOperator: CollectionOperator[ParticipantEvent]
  ): Task[Either[PersistenceError, String]] =
    EitherT(sequenceDao.getNextSequence(participantEventSequenceKey, seqOperator))
      .flatMap(seq =>
        EitherT(
          participantEventDao.insertOne(
            ParticipantEvent(
              participantIdentifier = participantIdentifier,
              participantDataModification = participantDataModification,
              eventId = seq,
              eventType = eventType
            ),
            participantEventOperator
          )
        )
      )
      .map(_ => participantIdentifier)
      .value
}
