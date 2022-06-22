package com.github.pandafolks.panda.healthcheck

import com.github.pandafolks.panda.nodestracker.NodeTrackerService
import com.github.pandafolks.panda.participant.{Participant, ParticipantsCache}
import com.github.pandafolks.panda.participant.event.ParticipantEventService
import monix.eval.Task
import org.http4s.Uri.{Authority, RegName}
import org.http4s.client.Client
import org.http4s.dsl.io.Path
import org.http4s.{Header, Request, Response, Uri}
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

  // todo mszmal: test

  import monix.execution.Scheduler.{global => scheduler}

  scheduler.scheduleAtFixedRate(0.seconds, 10.seconds) {
    backgroundJob().runToFuture(scheduler)
    ()
  }


  def backgroundJob(): Task[Unit] = {
    Task.parZip2(
      nodeTrackerService.getWorkingNodes.flatMap(nodes =>
        Task.eval(nodes.map(_._id.toString).indexOf(nodeTrackerService.getNodeId))
          .map {
            case index if index >= 0 && nodes.nonEmpty => (Some(nodes.size), Some(index))
            case _ => (Option.empty, Option.empty)
          }
      ),
      participantsCache.getAllWorkingParticipants
    ).flatMap {
      case ((Some(nodesSize), Some(index)), participants) =>
        Task
        Task.parTraverseUnordered(participants.filter(_.identifier.hashCode % nodesSize == index)) {
          participant =>
            performHeartbeatCall(participant)
              .map(_.status.isSuccess)
              .onErrorRecoverWith { _ => Task.now(false) }
              .map(heartbeatCallSuccessful =>
                if (heartbeatCallSuccessful)
                  if (participant.isHealthy) ??? // do nothing
                  else ??? // save event that participant is healthy and reset record in unhealthy table
                else ???
                // increment record in unhealthy table and insert event based on this info ..
              ) // todo mszmal: continue

        }.flatMap((_: Seq[Any]) => Task.unit)

      case ((_, _), _) => Task.unit
    }
  }

  private def performHeartbeatCall(participant: Participant): Task[Response[Task]] =
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

}
