package com.github.mattszm.panda.participant

import com.github.mattszm.panda.routes.Group
import com.github.mattszm.panda.utils.PersistenceError
import monix.eval.Task

trait ParticipantsCache {

  def getParticipantsAssociatedWithGroup(group: Group): Task[Vector[Participant]]

  def addParticipant(participant: Participant): Task[Either[PersistenceError, Unit]]

  def addParticipants(participants: List[Participant]): Task[Either[PersistenceError, Unit]]

  def removeParticipant(participant: Participant): Task[Either[PersistenceError, Unit]]

  def removeAllParticipantsAssociatedWithGroup(group: Group): Task[Either[PersistenceError, Unit]]
}
