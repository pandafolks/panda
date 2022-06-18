package com.github.pandafolks.panda.participant

import com.github.pandafolks.panda.routes.Group
import com.github.pandafolks.panda.utils.ChangeListener
import monix.eval.Task

trait ParticipantsCache {

  def getAllGroups: Task[List[Group]]

  def getParticipantsAssociatedWithGroup(group: Group): Task[Vector[Participant]]

  def getWorkingParticipantsAssociatedWithGroup(group: Group): Task[Vector[Participant]]

  def getHealthyParticipantsAssociatedWithGroup(group: Group): Task[Vector[Participant]]

  def registerListener(listener: ChangeListener[Participant]): Task[Unit]
}
