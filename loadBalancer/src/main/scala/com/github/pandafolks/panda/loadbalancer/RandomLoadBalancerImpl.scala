package com.github.pandafolks.panda.loadbalancer

import com.github.pandafolks.panda.participant.{Participant, ParticipantsCache}
import com.github.pandafolks.panda.routes.Group
import monix.eval.Task
import org.http4s.client.Client
import org.http4s.{Request, Response, Uri}
import org.slf4j.LoggerFactory

import scala.util.Random

/** The load balancer tries to hit all available participants. This is an important feature in the worst-case scenario. However, because of
  * that, the solution is relatively slow. The RandomLoadBalancer should be used only with a small number of participants. In other cases,
  * either the RoundRobinLoadBalancer or HashLoadBalancer should be preferred.
  */
final class RandomLoadBalancerImpl(private val client: Client[Task], private val participantsCache: ParticipantsCache)
    extends LoadBalancer {
  private val logger = LoggerFactory.getLogger(getClass.getName)

  override def route(request: Request[Task], requestedPath: Uri.Path, group: Group): Task[Response[Task]] = {
    def rc(participants: List[Participant]): Task[Response[Task]] = {
      if (participants.isEmpty) {
        LoadBalancer.notReachedAnyInstanceLog(requestedPath, group, logger)
      } else
        client
          .run(
            LoadBalancer.fillRequestWithParticipant(request, participants.head, requestedPath)
          )
          .use(Task.eval(_))
          .onErrorRecoverWith { case _: Throwable => rc(participants.tail) }
    }

    participantsCache.getHealthyParticipantsAssociatedWithGroup(group).map(_.toList).map(Random.shuffle(_)).flatMap {
      case emptyArray if emptyArray.isEmpty => LoadBalancer.noAvailableInstanceLog(requestedPath, group, logger)
      case nonEmptyShuffledArray            => rc(nonEmptyShuffledArray)
    }
  }

}
