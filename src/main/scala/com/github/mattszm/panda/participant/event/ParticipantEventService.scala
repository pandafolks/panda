package com.github.mattszm.panda.participant.event

import com.github.mattszm.panda.participant.dto.ParticipantModificationDto
import com.github.mattszm.panda.utils.PersistenceError
import monix.eval.Task

trait ParticipantEventService {
  def createParticipant(participantModificationDto: ParticipantModificationDto): Task[Either[PersistenceError, String]]

  def modifyParticipant(participantModificationDto: ParticipantModificationDto): Task[Either[PersistenceError, String]]

  def removeParticipant(participantIdentifier: String): Task[Either[PersistenceError, String]]
}
