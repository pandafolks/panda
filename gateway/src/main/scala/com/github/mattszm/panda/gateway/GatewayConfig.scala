package com.github.mattszm.panda.gateway

import com.github.mattszm.panda.loadbalancer.{ConsistentHashingState, HashLoadBalancerImpl, LoadBalancer, RandomLoadBalancerImpl, RoundRobinLoadBalancerImpl}
import com.github.mattszm.panda.participant.ParticipantsCache
import monix.eval.Task
import org.http4s.client.Client

final case class GatewayConfig(
                                mappingFile: String,
                                loadBalanceAlgorithm: LoadBalanceAlgorithm
                              )

sealed trait LoadBalanceAlgorithm {
  def create(client: Client[Task], participantsCache: ParticipantsCache): LoadBalancer
}

case object RoundRobin extends LoadBalanceAlgorithm {
  override def create(client: Client[Task], participantsCache: ParticipantsCache): LoadBalancer =
    new RoundRobinLoadBalancerImpl(client, participantsCache)
}

case object Random extends LoadBalanceAlgorithm {
  override def create(client: Client[Task], participantsCache: ParticipantsCache): LoadBalancer =
    new RandomLoadBalancerImpl(client, participantsCache)
}

case object Hash extends LoadBalanceAlgorithm {
  override def create(client: Client[Task], participantsCache: ParticipantsCache): LoadBalancer =
    new HashLoadBalancerImpl(client, participantsCache, new ConsistentHashingState())
}
