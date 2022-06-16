package com.github.mattszm.panda.participant

import com.github.mattszm.panda.routes.Group
import com.github.mattszm.panda.utils.ChangeListener
import monix.eval.Task

trait ParticipantsCache {

  def getAllGroups: Task[List[Group]]

  def getParticipantsAssociatedWithGroup(group: Group): Task[Vector[Participant]]

  def getWorkingParticipantsAssociatedWithGroup(group: Group): Task[Vector[Participant]]

  def registerListener(listener: ChangeListener[Participant]): Task[Unit]
}
