package com.github.mattszm.panda.participant.event

import com.github.mattszm.panda.participant.Participant
import com.github.mattszm.panda.participant.dto.ParticipantModificationDto
import com.github.mattszm.panda.utils.PersistenceError
import monix.eval.Task

trait ParticipantEventService {
  /**
   * Creates a new participant, if the one with the requested identifier does not exist
   *
   * @param participantModificationDto      Data transfer object with multiple configurable fields.
   *                                        The required ones are: host, port and groupName
   * @return                                Either empty if saved successfully or PersistenceError if the error during saving occurred
   */
  def createParticipant(participantModificationDto: ParticipantModificationDto): Task[Either[PersistenceError, String]]

  /**
   * Modifies participant configurable properties. Participant recognition is based on identifiers.
   *
   * @param participantModificationDto      Data transfer object with multiple configurable fields.
   * @return                                Either empty if saved successfully or PersistenceError if the error during saving occurred
   */
  def modifyParticipant(participantModificationDto: ParticipantModificationDto): Task[Either[PersistenceError, String]]

  /**
   * Removes a participant with a specified identifier if exists.
   *
   * @param participantIdentifier           A unique across all groups identifier
   * @return                                Either empty if removed successfully or PersistenceError if the error during removing occurred
   */
  def removeParticipant(participantIdentifier: String): Task[Either[PersistenceError, String]]

  /**
   * Returns all participants that exists in the persistence layer.
   *
   * @return                                List of the participants
   */
  def constructAllParticipants(): Task[List[Participant]]
}
