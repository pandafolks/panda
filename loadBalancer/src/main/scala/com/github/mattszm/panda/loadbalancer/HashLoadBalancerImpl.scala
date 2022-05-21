package com.github.mattszm.panda.loadbalancer

import com.github.mattszm.panda.participant.ParticipantsCache
import monix.eval.Task
import org.http4s.client.Client

final class HashLoadBalancerImpl(private val client: Client[Task],
                                 private val participantsCache: ParticipantsCache,
                                 private val consistentHashingState: ConsistentHashingState) {

}
