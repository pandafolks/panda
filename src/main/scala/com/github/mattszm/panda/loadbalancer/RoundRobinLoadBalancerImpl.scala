package com.github.mattszm.panda.loadbalancer

import com.github.mattszm.panda.participant.{Participant, ParticipantsCache}
import com.github.mattszm.panda.routes.Group
import monix.eval.Task
import monix.execution.atomic.AtomicInt
import org.http4s.Uri.{Authority, RegName}
import org.http4s.client.Client
import org.http4s.{Header, Request, Response, Uri}
import org.slf4j.LoggerFactory
import org.typelevel.ci.CIString

import java.util.concurrent.ConcurrentHashMap

final class RoundRobinLoadBalancerImpl(private val client: Client[Task],
                                       private val participantsCache: ParticipantsCache) extends LoadBalancer {
  private val logger = LoggerFactory.getLogger(getClass.getName)

  private val HOST_NAME: String = "host"

  private val lastUsedIndexes: ConcurrentHashMap[Group, AtomicInt] = new ConcurrentHashMap

  override def route(request: Request[Task], requestedPath: Uri.Path, group: Group): Task[Response[Task]] = {
    val eligibleParticipants: Vector[Participant] = participantsCache.getParticipantsAssociatedWithGroup(group)
    eligibleParticipants.size match {
      case 0 =>
        lastUsedIndexes.remove(group)
        logger.info("There is no available instance for the requested path: \"" + requestedPath.renderString + "\"")
        Response.notFoundFor(request)
      case size: Int =>
        Task.evalOnce(
          AtomicInt(lastUsedIndexes.computeIfAbsent(group, _ => AtomicInt(0))
            .getAndTransform(prev => (prev + 1) % Int.MaxValue) % size)
        ).map(atomicIndex => eligibleParticipants(atomicIndex.getAndIncrement() % size))
          .flatMap(chosenParticipant => {
            client.run(
              request
                .withUri(
                  request.uri.copy(
                    authority = Some(Authority(
                      host = RegName(chosenParticipant.host),
                      port = Some(chosenParticipant.port)
                    )),
                    path = requestedPath
                  )
                )
                .withHeaders(request.headers.put(Header.Raw(CIString(HOST_NAME), chosenParticipant.host)))
            ).use(Task.eval(_))
          })
          .onErrorRestart(eligibleParticipants.size - 1L) // trying to hit all available servers (in the worst case)
          .onErrorRecoverWith { case _: Throwable =>
            logger.info("[path: \"" + requestedPath.renderString + "\"]: " +
              "Could not reach any of the servers belonging to the group " + "\"" + group.name + "\"")
            Response.notFoundFor(request)
          }
    }
  }
}
