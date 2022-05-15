package com.github.mattszm.panda.participant.event

import com.github.mattszm.panda.utils.PersistenceError
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task

trait ParticipantEventDao {
  def exists(identifier: String, participantEventOperator: CollectionOperator[ParticipantEvent]): Task[Boolean]

  def insertOne(participantEvent: ParticipantEvent, participantEventOperator: CollectionOperator[ParticipantEvent]): Task[Either[PersistenceError, Unit]]
}
