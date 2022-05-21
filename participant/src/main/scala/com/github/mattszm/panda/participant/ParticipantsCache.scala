package com.github.mattszm.panda.participant

import com.github.mattszm.panda.routes.Group
import monix.eval.Task

trait ParticipantsCache {
  def getAllGroups: Task[List[Group]]

  def getParticipantsAssociatedWithGroup(group: Group): Task[Vector[Participant]]

  def addParticipant(participant: Participant): Task[Unit]

  def addParticipants(participants: List[Participant]): Task[Unit]

  def removeParticipant(participant: Participant): Task[Unit]

  def removeAllParticipantsAssociatedWithGroup(group: Group): Task[Unit]
}
