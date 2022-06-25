package com.github.pandafolks.panda.healthcheck

import com.github.pandafolks.panda.nodestracker.NodeTrackerService
import com.github.pandafolks.panda.participant.event.ParticipantEventService
import com.github.pandafolks.panda.participant.{Participant, ParticipantsCache}
import com.github.pandafolks.panda.utils.ChangeListener
import com.google.common.annotations.VisibleForTesting
import monix.eval.Task
import org.http4s.Uri.{Authority, RegName}
import org.http4s.client.Client
import org.http4s.dsl.io.Path
import org.http4s.{Header, Request, Uri}
import org.slf4j.LoggerFactory
import org.typelevel.ci.CIString

import java.util.concurrent.ConcurrentHashMap
import scala.collection.immutable
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
                                             )(private val healthCheckConfig: HealthCheckConfig)
  extends HealthCheckService with ChangeListener[Participant] {

  private val logger = LoggerFactory.getLogger(getClass.getName)

  private sealed trait EmittedEventType

  private object EmittedEventType {

    case object MarkedParticipantAsHealthy extends EmittedEventType

    case object MarkedParticipantAsUnhealthy extends EmittedEventType

  }

  // HealthCheckConfig#callsInterval and ConsistencyConfig#fullConsistencyMaxDelay are two independent params. In order to not produce multiple Connection events this map was added.
  private val eventsEmittedSinceLastCacheRefresh: ConcurrentHashMap[String, EmittedEventType] = new ConcurrentHashMap

  locally {
    import monix.execution.Scheduler.{global => scheduler}

    if (healthCheckConfig.callsInterval > 0 && healthCheckConfig.numberOfFailuresNeededToReact > 0) {
      scheduler.scheduleAtFixedRate(0.seconds, healthCheckConfig.callsInterval.seconds) {
        backgroundJob()
          .onErrorRecover { e: Throwable => logger.error(s"Cannot perform healthcheck job on this node. [Node ID: ${nodeTrackerService.getNodeId}]", e) }
          .runToFuture(scheduler)
        ()
      }
    }
  }

  private def backgroundJob(): Task[Unit] = {
    Task.parZip2(
      getNodesSizeWithCurrentNodePosition,
      participantsCache.getAllWorkingParticipants
    ).flatMap {
      case ((Some(nodesSize), Some(currentNodeIndex)), participants) =>
        Task.parTraverseUnordered(pickParticipantsForNode(participants, nodesSize, currentNodeIndex)) {
          participant =>
            // We are reading eventEmittedSinceLastCacheRefresh as early as possible because there is the scenario
            // when the health check could take some time and in the meantime, cache got refreshed and
            // eventsEmittedSinceLastCacheRefresh cleared and as a result, we would insert redundant event - as this is
            // not the end of the world and we handle this in the proper way, reading this value earlier minimizes the risk.
          val eventEmittedSinceLastCacheRefresh = Option(eventsEmittedSinceLastCacheRefresh.get(participant.identifier))
            performHealthcheckCallAndReturnResult(participant)
              .flatMap {
                // Healthcheck successful, but the latest participant state inside cache was not healthy, so we are marking participant as healthy one and resetting related failed healthchecks counter.
                case true if !participant.isHealthy =>
                  unsuccessfulHealthCheckDao.clear(participant.identifier)
                    .map {
                      case Left(error) =>
                        logger.error(s"Unsuccessful healthcheck counter of ${participant.identifier} not cleared because of error.")
                        logger.error(error.getMessage)
                        ()
                      case _ => ()
                    } >> Task.eval(eventEmittedSinceLastCacheRefresh.fold(true)(_ != EmittedEventType.MarkedParticipantAsHealthy))
                    .flatMap { eventNeedsToBeEmitted =>
                      if (eventNeedsToBeEmitted)
                        participantEventService.markParticipantAsHealthy(participant.identifier)
                          .map { markAsHealthyResult =>
                            if (markAsHealthyResult.isRight) {
                              eventsEmittedSinceLastCacheRefresh.put(participant.identifier, EmittedEventType.MarkedParticipantAsHealthy)
                            }
                            markAsHealthyResult
                          }
                      else Task.now(Right(()))
                    }.map {
                    case Left(error) =>
                      logger.error(s"${participant.identifier} could not be marked as healthy because of error.")
                      logger.error(error.getMessage)
                      ()
                    case _ => ()
                  }

                // Healthcheck failed, but the latest participant state inside cache was healthy, so we are incrementing related failed healthchecks counter and if the counter reaches specified limit we mark participant as not healthy.
                case false if participant.isHealthy =>
                  unsuccessfulHealthCheckDao.incrementCounter(participant.identifier).map {
                    case Right(counter)
                      if counter >= healthCheckConfig.numberOfFailuresNeededToReact
                        && eventEmittedSinceLastCacheRefresh.fold(true)(_ != EmittedEventType.MarkedParticipantAsUnhealthy) => true
                    case Right(_) => false
                    case Left(error) =>
                      logger.error(s"Unsuccessful healthcheck counter of ${participant.identifier} not incremented because of error.")
                      logger.error(error.getMessage)
                      false
                  }.flatMap { eventNeedsToBeEmitted =>
                    if (eventNeedsToBeEmitted)
                      participantEventService.markParticipantAsUnhealthy(participant.identifier)
                        .map { markAsUnhealthyResult =>
                          if (markAsUnhealthyResult.isRight) {
                            eventsEmittedSinceLastCacheRefresh.put(participant.identifier, EmittedEventType.MarkedParticipantAsUnhealthy)
                          }
                          markAsUnhealthyResult
                        }
                    else Task.now(Right(()))
                  }.map {
                    case Left(error) =>
                      logger.error(s"${participant.identifier} could not be marked as unhealthy because of error.")
                      logger.error(error.getMessage)
                      ()
                    case _ => ()
                  }

                // 2 scenarios where actions are not needed:
                //    - healthcheck successful + participant healthy (all good)
                //    - healthcheck failed + participant not healthy (we already are aware that participant is not healthy, so no event needs to be emitted)
                case _ => Task.unit
              }
        }.flatMap(_ => Task.unit)
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

  private def performHealthcheckCallAndReturnResult(participant: Participant): Task[Boolean] =
    client.run(
      Request[Task]().withUri(
        Uri(
          authority = Some(Authority(
            host = RegName(participant.host),
            port = Some(participant.port)
          )),
          path = Path.unsafeFromString(participant.healthcheckInfo.path)
        )
      ).withHeaders(Header.Raw(CIString("host"), participant.host))
    ).use(Task.eval(_))
      .map(_.status.isSuccess)
      .onErrorRecoverWith { _ => Task.now(false) }

  // Once the cache is refreshed, we can assume that if the event was inserted it already should be present in the cache.
  // Even if there will be some race (cache refresh is quite expensive) and we will clear too early - this is ok
  // because in the worst scenario there will be a second event inserted (the default logic
  // saves an event if eventsEmittedSinceLastCacheRefresh does not contain the identifier that was asked)
  override def notifyAboutAdd(items: immutable.Iterable[Participant]): Task[Unit] = Task.evalAsync(eventsEmittedSinceLastCacheRefresh.clear())

  override def notifyAboutRemove(items: immutable.Iterable[Participant]): Task[Unit] = Task.unit // no action needs to be taken
}
