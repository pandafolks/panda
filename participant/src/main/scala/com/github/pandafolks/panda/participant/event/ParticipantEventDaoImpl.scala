package com.github.pandafolks.panda.participant.event

import cats.data.OptionT
import cats.effect.Resource
import ParticipantEventType.{Created, Removed}
import com.github.pandafolks.panda.utils.{PersistenceError, UndefinedPersistenceError, UnsuccessfulSaveOperation}
import com.mongodb.client.model.Filters
import com.pandafolks.mattszm.panda.sequence.Sequence
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task
import monix.reactive.Observable
import org.mongodb.scala.bson.BsonInt64
import org.mongodb.scala.model.{Aggregates, Sorts}

final class ParticipantEventDaoImpl(
    private val c: Resource[Task, (CollectionOperator[ParticipantEvent], CollectionOperator[Sequence])]
) extends ParticipantEventDao {

  override def exists(
      identifier: String,
      participantEventOperator: CollectionOperator[ParticipantEvent]
  ): Task[Either[PersistenceError, Boolean]] =
    OptionT(
      participantEventOperator.source
        .aggregate(
          List(
            Aggregates.filter(Filters.eq(ParticipantEvent.PARTICIPANT_IDENTIFIER_PROPERTY_NAME, identifier)),
            Aggregates.sort(Sorts.descending(ParticipantEvent.EVENT_ID_PROPERTY_NAME))
          ),
          classOf[ParticipantEvent]
        )
        .filter(event => event.eventType == Created() || event.eventType == Removed())
        .firstOptionL
    )
      .filter(event => event.eventType != Removed())
      .value
      .map(o => Right(o.isDefined))
      .onErrorRecoverWith { case t: Throwable => Task.now(Left(UndefinedPersistenceError(t.getMessage))) }

  override def insertOne(
      participantEvent: ParticipantEvent,
      participantEventOperator: CollectionOperator[ParticipantEvent]
  ): Task[Either[PersistenceError, Unit]] =
    participantEventOperator.single
      .insertOne(participantEvent)
      .map(_ => Right(()))
      .onErrorRecoverWith { case t: Throwable => Task.now(Left(UnsuccessfulSaveOperation(t.getMessage))) }

  override def getOrderedEvents(
      participantEventOperator: CollectionOperator[ParticipantEvent],
      offset: Int
  ): Observable[ParticipantEvent] =
    participantEventOperator.source.aggregate(
      List(
        Aggregates.filter(Filters.gt(ParticipantEvent.EVENT_ID_PROPERTY_NAME, offset)),
        Aggregates.sort(Sorts.ascending(ParticipantEvent.EVENT_ID_PROPERTY_NAME))
      ),
      classOf[ParticipantEvent]
    )

  override def checkIfThereAreNewerEvents(eventId: Long): Task[Boolean] = c.use { case (participantEventOperator, _) =>
    participantEventOperator.source
      .aggregate(
        List(
          Aggregates.filter(Filters.gt(ParticipantEvent.EVENT_ID_PROPERTY_NAME, BsonInt64(eventId))),
          Aggregates.limit(1)
        ),
        classOf[ParticipantEvent]
      )
      .nonEmptyL
  }
}
