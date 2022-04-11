package com.mattszm.panda.participants

import scala.collection.immutable.MultiDict

final class ParticipantsCacheImpl(val participants: List[Participant]) extends ParticipantsCache {
  // Temporary solution!
  // The cache should be provided to the Load Balancer.
  // When updating comes in, this probably needs to be migrated to guava's multimap in order to be thread safe.
  val cacheByGroup: MultiDict[String, Participant] = MultiDict.from(participants.map(p => (p.group, p)))
}
