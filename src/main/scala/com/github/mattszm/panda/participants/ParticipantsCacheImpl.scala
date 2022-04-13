package com.github.mattszm.panda.participants

import com.github.mattszm.panda.routes.Group

import scala.collection.immutable.MultiDict

final class ParticipantsCacheImpl(private val participants: List[Participant]) extends ParticipantsCache {
  // Temporary solution!
  // The cache should be provided to the Load Balancer.
  // When updating comes in, this probably needs to be migrated to guava's multimap in order to be thread safe.
  private val cacheByGroup: MultiDict[Group, Participant] = MultiDict.from(participants.map(p => (p.group, p)))

  override def getParticipantsAssociatedWithGroup(group: Group): List[Participant] = cacheByGroup.get(group).toList
}
