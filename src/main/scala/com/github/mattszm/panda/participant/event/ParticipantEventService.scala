package com.github.mattszm.panda.participant.event

import com.github.mattszm.panda.participant.dto.ParticipantCreationDto
import com.github.mattszm.panda.utils.PersistenceError
import monix.eval.Task

trait ParticipantEventService {
  def createParticipant(participantCreationDto: ParticipantCreationDto): Task[Either[PersistenceError, Unit]]
}
