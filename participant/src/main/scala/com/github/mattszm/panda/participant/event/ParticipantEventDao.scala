package com.github.mattszm.panda.participant.event

import com.github.mattszm.panda.utils.PersistenceError
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task

trait ParticipantEventDao {
  /**
   * Checks whether participant with requested identifier exists.
   *
   * @param identifier                  Participant unique identifier
   * @param participantEventOperator    Participant DB entry point
   * @return                            True if exists, false otherwise
   */
  def exists(identifier: String, participantEventOperator: CollectionOperator[ParticipantEvent]): Task[Boolean]

  /**
   * Inserts participant event into the persistence layer.
   *
   * @param participantEvent            Participant event represented as an object of common type for all types of events
   * @param participantEventOperator    Participant DB entry point
   * @return                            Either empty if saved successfully or PersistenceError if the error during saving occurred
   */
  def insertOne(participantEvent: ParticipantEvent, participantEventOperator: CollectionOperator[ParticipantEvent]): Task[Either[PersistenceError, Unit]]
}
