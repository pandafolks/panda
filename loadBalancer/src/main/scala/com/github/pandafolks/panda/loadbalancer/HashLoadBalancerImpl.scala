package com.github.pandafolks.panda.loadbalancer

import com.github.pandafolks.panda.participant.ParticipantsCache
import com.github.pandafolks.panda.routes.Group
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.http4s.client.Client
import org.http4s.{Request, Response, Uri}
import org.slf4j.LoggerFactory

import scala.concurrent.duration.DurationInt
import scala.util.Random
import scala.util.hashing.MurmurHash3

final class HashLoadBalancerImpl(private val client: Client[Task],
                                 private val participantsCache: ParticipantsCache,
                                 private val consistentHashingState: ConsistentHashingState,
                                 private val retriesNumber: Int = 10) extends LoadBalancer {
  private val logger = LoggerFactory.getLogger(getClass.getName)

  private val random = new Random(System.currentTimeMillis())

  locally {
    participantsCache.registerListener(consistentHashingState).runSyncUnsafe(30.seconds)
  }

  override def route(request: Request[Task], requestedPath: Uri.Path, group: Group): Task[Response[Task]] = {
    def rc(hash: Int, leftTriesNumber: Int = retriesNumber + 1): Task[Response[Task]] =
      if (leftTriesNumber == 0) {
        LoadBalancer.notReachedAnyInstanceLog(requestedPath, group, logger)
        Response.notFoundFor(request)
      } else {
        Task.eval(consistentHashingState.get(group, hash))
          .flatMap(participantOption =>
            participantOption.fold(Task.now(LoadBalancer.noAvailableInstanceLog(requestedPath, logger))
              .flatMap(_ => Response.notFoundFor(request)))(participant =>
              client.run(LoadBalancer.fillRequestWithParticipant(request, participant, requestedPath)).use(Task.eval(_))
            )
          ).onErrorRecoverWith { case _: Throwable => rc(random.nextInt(Integer.MAX_VALUE), leftTriesNumber - 1) }
      }

    rc(Math.abs(MurmurHash3.stringHash(
      request.remote.map(_.host.toUriString).getOrElse(random.nextInt(Integer.MAX_VALUE).toString)
      // If the connectionInfo is not accessible the value should be random in order to spread requests evenly instead of
      // fixed value which will route all requests to single instance.
    )))
  }
}
