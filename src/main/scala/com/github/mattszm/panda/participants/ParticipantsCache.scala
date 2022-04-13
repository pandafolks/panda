package com.github.mattszm.panda.participants

import com.github.mattszm.panda.routes.Group

trait ParticipantsCache {
  // provide methods useful for loadBalancer
  def getParticipantsAssociatedWithGroup(group: Group): List[Participant]
}
