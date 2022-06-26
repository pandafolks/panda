package com.github.pandafolks.panda.gateway

import com.github.pandafolks.panda.loadbalancer.{ConsistentHashingState, HashLoadBalancerImpl, LoadBalancer, RandomLoadBalancerImpl, RoundRobinLoadBalancerImpl}
import com.github.pandafolks.panda.participant.ParticipantsCache
import monix.eval.Task
import org.http4s.client.Client

final case class GatewayConfig(
                                mappingFile: String,
                                loadBalancerAlgorithm: LoadBalanceAlgorithm,
                                loadBalancerRetries: Option[Int]
                              )

sealed trait LoadBalanceAlgorithm {
  def create(client: Client[Task], participantsCache: ParticipantsCache, loadBalancerRetries: Option[Int] = Option.empty): LoadBalancer
}

case object RoundRobin extends LoadBalanceAlgorithm {
  override def create(client: Client[Task], participantsCache: ParticipantsCache, loadBalancerRetries: Option[Int] = Option.empty): LoadBalancer =
    new RoundRobinLoadBalancerImpl(client, participantsCache)
}

case object Random extends LoadBalanceAlgorithm {
  override def create(client: Client[Task], participantsCache: ParticipantsCache, loadBalancerRetries: Option[Int] = Option.empty): LoadBalancer =
    new RandomLoadBalancerImpl(client, participantsCache)
}

case object Hash extends LoadBalanceAlgorithm {
  override def create(client: Client[Task], participantsCache: ParticipantsCache, loadBalancerRetries: Option[Int] = Option.empty): LoadBalancer =
    new HashLoadBalancerImpl(client, participantsCache, new ConsistentHashingState(), loadBalancerRetries.getOrElse(10))
}
