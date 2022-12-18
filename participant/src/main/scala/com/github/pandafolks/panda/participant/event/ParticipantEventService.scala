package com.github.pandafolks.panda.participant.event

import com.github.pandafolks.panda.participant.{Participant, ParticipantModificationPayload}
import com.github.pandafolks.panda.utils.PersistenceError
import monix.eval.Task

trait ParticipantEventService {

  /** Creates a new participant, if the one with the requested identifier does not exist
    *
    * @param participantModificationPayload
    *   Data transfer object with multiple configurable fields. The required ones are: host, port and groupName
    * @return
    *   Either identifier if saved successfully or PersistenceError if the error during saving occurred
    */
  def createParticipant(
      participantModificationPayload: ParticipantModificationPayload
  ): Task[Either[PersistenceError, String]]

  /** Modifies participant configurable properties. Participant recognition is based on identifiers.
    *
    * @param participantModificationPayload
    *   Data transfer object with multiple configurable fields.
    * @return
    *   Either identifier if saved successfully or PersistenceError if the error during saving occurred
    */
  def modifyParticipant(
      participantModificationPayload: ParticipantModificationPayload
  ): Task[Either[PersistenceError, String]]

  /** Removes a participant with a specified identifier if exists.
    *
    * @param participantIdentifier
    *   A unique across all groups identifier
    * @return
    *   Either identifier if removed successfully or PersistenceError if the error during removing occurred
    */
  def removeParticipant(participantIdentifier: String): Task[Either[PersistenceError, String]]

  /** Returns all participants that exist in the persistence layer together with the highest seen event ID.
    *
    * @return
    *   Tuple with list of the participants and event ID
    */
  def constructAllParticipants(): Task[(List[Participant], Long)]

  /** Marks a participant with a specified identifier as connected
    * [[com.github.pandafolks.panda.participant.event.ParticipantEventType.Joined]], in other words healthy.
    *
    * @param participantIdentifier
    *   A unique across all groups identifier
    * @return
    *   Either empty if marked successfully or PersistenceError if the error occurred
    */
  def markParticipantAsHealthy(participantIdentifier: String): Task[Either[PersistenceError, Unit]]

  /** Marks a participant with a specified identifier as
    * [[com.github.pandafolks.panda.participant.event.ParticipantEventType.Disconnected]], in other words unhealthy.
    *
    * @param participantIdentifier
    *   A unique across all groups identifier
    * @return
    *   Either empty if marked successfully or PersistenceError if the error occurred
    */
  def markParticipantAsUnhealthy(participantIdentifier: String): Task[Either[PersistenceError, Unit]]

  /** Checks whether there is at least one event with an ID higher than provided eventId.
    *
    * @param eventId
    *   Determining how many events to discard
    * @return
    *   True if there are events with higher ID, false otherwise
    */
  def checkIfThereAreNewerEvents(eventId: Long): Task[Boolean]

  /** Marks a participant with a specified identifier as [[com.github.pandafolks.panda.participant.event.ParticipantEventType.TurnedOff]],
    * which will end up with participant with [[com.github.pandafolks.panda.participant.NotWorking]] status.
    *
    * @param participantIdentifier
    *   A unique across all groups identifier
    * @return
    *   Either empty if marked successfully or PersistenceError if the error occurred
    */
  def markParticipantAsTurnedOff(participantIdentifier: String): Task[Either[PersistenceError, Unit]]
}
