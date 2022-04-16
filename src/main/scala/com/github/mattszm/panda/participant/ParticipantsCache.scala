package com.github.mattszm.panda.participant

import com.github.mattszm.panda.routes.Group

trait ParticipantsCache {

  def getParticipantsAssociatedWithGroup(group: Group): Vector[Participant]
}
