package com.github.mattszm.panda.participant.event

import cats.effect.Resource
import com.github.mattszm.panda.sequence.Sequence
import com.github.mattszm.panda.utils.{PersistenceError, UnsuccessfulSaveOperation}
import com.mongodb.client.model.Filters
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task

final class ParticipantEventDaoImpl(private val c: Resource[Task, (CollectionOperator[ParticipantEvent],
  CollectionOperator[Sequence])]) extends ParticipantEventDao {

  override def exists(identifier: String, participantEventOperator: CollectionOperator[ParticipantEvent]): Task[Boolean] =
    participantEventOperator.source
      .find(Filters.eq("participantIdentifier", identifier))
      .firstOptionL
      .map(_.isDefined)

  override def insertOne(participantEvent: ParticipantEvent,
                         participantEventOperator: CollectionOperator[ParticipantEvent]
                        ): Task[Either[PersistenceError, Unit]] =
    participantEventOperator.single.insertOne(participantEvent)
      .map(_ => Right(()))
      .onErrorRecoverWith {
        case t: Throwable => Task.now(Left(UnsuccessfulSaveOperation(t.getMessage)))
      }
}
