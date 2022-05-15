package com.github.mattszm.panda.loadbalancer

import com.github.mattszm.panda.participant.{Participant, ParticipantsCache}
import com.github.mattszm.panda.routes.Group
import monix.eval.Task
import org.http4s.client.Client
import org.http4s.{Request, Response, Uri}
import org.slf4j.LoggerFactory

import scala.util.Random

final class RandomLoadBalancerImpl(private val client: Client[Task],
                                   private val participantsCache: ParticipantsCache) extends LoadBalancer {
  private val logger = LoggerFactory.getLogger(getClass.getName)

  override def route(request: Request[Task], requestedPath: Uri.Path, group: Group): Task[Response[Task]] = {
    def rc(participants: List[Participant]): Task[Response[Task]] = {
      if (participants.isEmpty) {
        LoadBalancer.notReachedAnyInstanceLog(requestedPath, group, logger)
        Response.notFoundFor(request)
      } else client.run(
        LoadBalancer.fillRequestWithParticipant(request, participants.head, requestedPath)
      ).use(Task.eval(_))
        .onErrorRecoverWith { case _: Throwable => rc(participants.tail) }
    }

    participantsCache.getParticipantsAssociatedWithGroup(group).map(_.toList).map(Random.shuffle(_)).flatMap {
      case emptyArray if emptyArray.isEmpty =>
        LoadBalancer.noAvailableInstanceLog(requestedPath, logger)
        Response.notFoundFor(request)
      case nonEmptyShuffledArray => rc(nonEmptyShuffledArray)
    }
  }

}
