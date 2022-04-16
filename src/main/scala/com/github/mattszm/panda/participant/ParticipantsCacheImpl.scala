package com.github.mattszm.panda.participant

import com.github.mattszm.panda.routes.Group

import scala.collection.immutable.MultiDict

final class ParticipantsCacheImpl(private val initParticipants: List[Participant]) extends ParticipantsCache {
  // When updating comes in, this probably needs to be migrated to guava's multimap in order to be thread safe.
  private val cacheByGroup: MultiDict[Group, Participant] = MultiDict.from(initParticipants.map(p => (p.group, p)))

  override def getParticipantsAssociatedWithGroup(group: Group): Vector[Participant] = cacheByGroup.get(group).toVector
}
