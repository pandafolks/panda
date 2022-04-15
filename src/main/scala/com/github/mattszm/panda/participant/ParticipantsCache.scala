package com.github.mattszm.panda.participant

import com.github.mattszm.panda.routes.Group

trait ParticipantsCache {
  // provide methods useful for loadBalancer
  def getParticipantsAssociatedWithGroup(group: Group): Vector[Participant]
}
