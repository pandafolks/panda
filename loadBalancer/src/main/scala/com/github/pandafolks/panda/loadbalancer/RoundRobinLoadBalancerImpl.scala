package com.github.pandafolks.panda.loadbalancer

import com.github.pandafolks.panda.participant.ParticipantsCache
import com.github.pandafolks.panda.routes.Group
import monix.eval.Task
import monix.execution.atomic.AtomicInt
import org.http4s.client.Client
import org.http4s.{Request, Response, Uri}
import org.slf4j.LoggerFactory

import java.util.concurrent.ConcurrentHashMap

final class RoundRobinLoadBalancerImpl(private val client: Client[Task],
                                       private val participantsCache: ParticipantsCache) extends LoadBalancer {
  private val logger = LoggerFactory.getLogger(getClass.getName)

  private val lastUsedIndexes: ConcurrentHashMap[Group, AtomicInt] = new ConcurrentHashMap

  override def route(request: Request[Task], requestedPath: Uri.Path, group: Group): Task[Response[Task]] =
    participantsCache.getHealthyParticipantsAssociatedWithGroup(group).flatMap {
      case eligibleParticipants if eligibleParticipants.isEmpty =>
        lastUsedIndexes.remove(group)
        LoadBalancer.noAvailableInstanceLog(requestedPath, logger)
        Response.notFoundFor(request)
      case eligibleParticipants =>
        Task.evalOnce(
          AtomicInt(lastUsedIndexes.computeIfAbsent(group, _ => AtomicInt(0))
            .getAndTransform(prev => (prev + 1) % Int.MaxValue) % eligibleParticipants.size)
        ).map(atomicIndex => eligibleParticipants(atomicIndex.getAndIncrement() % eligibleParticipants.size))
          .flatMap(chosenParticipant => {
            client.run(
              LoadBalancer.fillRequestWithParticipant(request, chosenParticipant, requestedPath)
            ).use(Task.eval(_))
          })
          .onErrorRestart(eligibleParticipants.size - 1L) // trying to hit all available servers (in the worst case)
          .onErrorRecoverWith { case _: Throwable =>
            LoadBalancer.notReachedAnyInstanceLog(requestedPath, group, logger)
            Response.notFoundFor(request)
          }
    }
}
