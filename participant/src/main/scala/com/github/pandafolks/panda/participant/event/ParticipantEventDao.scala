package com.github.pandafolks.panda.participant.event

import com.github.pandafolks.panda.utils.PersistenceError
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task
import monix.reactive.Observable

trait ParticipantEventDao {
  /**
   * Checks whether participant with requested identifier exists.
   * If there was a Removed item emitted and there was no Created event after it - the identifier is
   * recognized as not exist.
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

  /**
   * Return stream of all available events ordered by event IDs with the IDs higher than offset.
   *
   * @param participantEventOperator    Participant DB entry point
   * @param offset                      Describes maximum discarded event identifier
   * @return                            Stream of the participant events
   */
  def getOrderedEvents(participantEventOperator: CollectionOperator[ParticipantEvent], offset: Int = -1): Observable[ParticipantEvent]
}
