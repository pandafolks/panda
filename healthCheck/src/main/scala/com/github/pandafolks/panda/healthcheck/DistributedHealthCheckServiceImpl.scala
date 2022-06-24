package com.github.pandafolks.panda.healthcheck

import com.github.pandafolks.panda.nodestracker.NodeTrackerService
import com.github.pandafolks.panda.participant.event.ParticipantEventService
import com.github.pandafolks.panda.participant.{Participant, ParticipantsCache}
import com.google.common.annotations.VisibleForTesting
import monix.eval.Task
import org.http4s.Uri.{Authority, RegName}
import org.http4s.client.Client
import org.http4s.dsl.io.Path
import org.http4s.{Header, Request, Uri}
import org.typelevel.ci.CIString

import scala.concurrent.duration.DurationInt

/**
 * This is a distributed implementation of [[HealthCheckService]].
 * Distributed in this scenario means it supports by default multi-node Panda configurations and makes use
 * of such configurations in terms of efficiency and splitting health check calls across multiple nodes.
 */
final class DistributedHealthCheckServiceImpl(private val participantEventService: ParticipantEventService,
                                              private val participantsCache: ParticipantsCache,
                                              private val nodeTrackerService: NodeTrackerService,
                                              private val unsuccessfulHealthCheckDao: UnsuccessfulHealthCheckDao,
                                              private val client: Client[Task],
                                             )(private val healthCheckConfig: HealthCheckConfig) extends HealthCheckService {

  locally {
    // todo mszmal: test

    import monix.execution.Scheduler.{global => scheduler}

    if (healthCheckConfig.callsInterval > 0 && healthCheckConfig.numberOfAllowedFails > 0) {
      scheduler.scheduleAtFixedRate(0.seconds, 10.seconds) {
        backgroundJob().runToFuture(scheduler)
        ()
      }
    }
  }

  def backgroundJob(): Task[Unit] = {
    Task.parZip2(
      getNodesSizeWithCurrentNodePosition,
      participantsCache.getAllWorkingParticipants
    ).flatMap {
      case ((Some(nodesSize), Some(currentNodeIndex)), participants) =>
        Task.parTraverseUnordered(pickParticipantsForNode(participants, nodesSize, currentNodeIndex)) {
          participant =>
            performHeartbeatCallAndReturnResult(participant)
              .map {
                case true if !participant.isHealthy =>
                case false if participant.isHealthy =>
                // 2 scenarios where actions are not needed:
                //    - hearthbeat successful + participant healthy (all good)
                //    - hearthbeat failed + participant not healthy (we already are aware that participant is not healthy, so no event needs to be emitted)
                case _ => Task.unit

              }



        }.flatMap((_: Seq[Any]) => Task.unit)

      case ((_, _), _) => Task.unit
    }
  }

  @VisibleForTesting
  private def getNodesSizeWithCurrentNodePosition: Task[(Option[Int], Option[Int])] =
    nodeTrackerService.getWorkingNodes.flatMap(nodes =>
      Task.eval(nodes.map(_._id.toString).indexOf(nodeTrackerService.getNodeId))
        .map {
          case index if index >= 0 => (Some(nodes.size), Some(index))
          case _ => (Option.empty, Option.empty)
        }
    )

  @VisibleForTesting
  private def pickParticipantsForNode(participants: List[Participant], nodesSize: Int, nodeIndex: Int): List[Participant] =
    participants.filter(_.identifier.hashCode % nodesSize == nodeIndex)

  private def performHeartbeatCallAndReturnResult(participant: Participant): Task[Boolean] =
    client.run(
      Request[Task]().withUri(
        Uri(
          authority = Some(Authority(
            host = RegName(participant.host),
            port = Some(participant.port)
          )),
          path = Path.unsafeFromString(participant.heartbeatInfo.path)
        )
      ).withHeaders(Header.Raw(CIString("host"), participant.host))
    ).use(Task.eval(_))
      .map(_.status.isSuccess)
      .onErrorRecoverWith { _ => Task.now(false) }

}
