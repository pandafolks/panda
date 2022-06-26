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
import org.mongodb.scala.model.{Aggregates, Sorts}

final class ParticipantEventDaoImpl(private val c: Resource[Task, (CollectionOperator[ParticipantEvent],
  CollectionOperator[Sequence])]) extends ParticipantEventDao {

  override def exists(identifier: String, participantEventOperator: CollectionOperator[ParticipantEvent]): Task[Either[PersistenceError, Boolean]] =
    OptionT(participantEventOperator.source.aggregate(
      List(
        Aggregates.filter(Filters.eq("participantIdentifier", identifier)),
        Aggregates.sort(Sorts.descending("eventId"))
      ), classOf[ParticipantEvent]
    )
      .filter(event => event.eventType == Created() || event.eventType == Removed())
      .firstOptionL
    )
      .filter(event => event.eventType != Removed())
      .value
      .map(o => Right(o.isDefined))
      .onErrorRecoverWith { case t: Throwable => Task.now(Left(UndefinedPersistenceError(t.getMessage))) }


  override def insertOne(participantEvent: ParticipantEvent,
                         participantEventOperator: CollectionOperator[ParticipantEvent]): Task[Either[PersistenceError, Unit]] =
    participantEventOperator.single.insertOne(participantEvent)
      .map(_ => Right(()))
      .onErrorRecoverWith { case t: Throwable => Task.now(Left(UnsuccessfulSaveOperation(t.getMessage))) }

  override def getOrderedEvents(participantEventOperator: CollectionOperator[ParticipantEvent],
                                offset: Int): Observable[ParticipantEvent] =
    participantEventOperator.source.aggregate(
      List(
        Aggregates.filter(Filters.gt("eventId", offset)),
        Aggregates.sort(Sorts.ascending("eventId")),
      ), classOf[ParticipantEvent]
    )
}
