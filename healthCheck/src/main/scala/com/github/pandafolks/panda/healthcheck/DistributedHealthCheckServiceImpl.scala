package com.github.pandafolks.panda.healthcheck

import cats.data.OptionT
import com.github.pandafolks.panda.backgroundjobsregistry.BackgroundJobsRegistry
import com.github.pandafolks.panda.nodestracker.NodeTrackerService
import com.github.pandafolks.panda.participant.event.ParticipantEventService
import com.github.pandafolks.panda.participant.{Participant, ParticipantsCache}
import com.github.pandafolks.panda.utils.NotExists
import com.github.pandafolks.panda.utils.http.RequestUtils
import com.github.pandafolks.panda.utils.listener.ChangeListener
import com.google.common.annotations.VisibleForTesting
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.schedulers.CanBlock
import org.http4s.Uri.{Authority, RegName}
import org.http4s.client.Client
import org.http4s.dsl.io.Path
import org.http4s.{Request, Uri}
import org.slf4j.LoggerFactory

import java.util.concurrent.ConcurrentHashMap
import scala.collection.immutable
import scala.concurrent.duration.DurationInt
import scala.util.hashing.MurmurHash3

/** This is a distributed implementation of [[HealthCheckService]]. Distributed in this scenario means it supports by default multi-node
  * Panda configurations and makes use of such configurations in terms of efficiency and splitting health check calls across multiple nodes.
  */
final class DistributedHealthCheckServiceImpl(
    private val participantEventService: ParticipantEventService,
    private val participantsCache: ParticipantsCache,
    private val nodeTrackerService: NodeTrackerService,
    private val unsuccessfulHealthCheckDao: UnsuccessfulHealthCheckDao,
    private val client: Client[Task],
    private val backgroundJobsRegistry: BackgroundJobsRegistry
)(private val healthCheckConfig: HealthCheckConfig)(private val scheduler: Scheduler)
    extends HealthCheckService
    with ChangeListener[Participant] {

  private val logger = LoggerFactory.getLogger(getClass.getName)

  private val MARKING_PARTICIPANTS_AS_EITHER_TURNED_OFF_OR_REMOVED_JOB_NAME =
    "MarkingParticipantsAsEitherTurnedOffOrRemoved"

  private val markAsNotWorkingDeviationInMillis: Option[Long] =
    healthCheckConfig.getSmallerMarkedAsDelay.map(_ * 1000L) // the original value is in seconds

  private sealed trait EmittedEventType

  private object EmittedEventType {

    case object MarkedParticipantAsHealthy extends EmittedEventType

    case object MarkedParticipantAsUnhealthy extends EmittedEventType

  }

  private sealed trait MarkAsNotWorkingResult {
    def getRelatedIdentifier: String
  }

  private object MarkAsNotWorkingResult {
    case class MarkedAsRemoved(private val identifier: String) extends MarkAsNotWorkingResult {
      override def getRelatedIdentifier: String = identifier
    }

    case class MarkedAsTurnedOff(private val identifier: String) extends MarkAsNotWorkingResult {
      override def getRelatedIdentifier: String = identifier
    }
  }

  // HealthCheckConfig#callsInterval and ConsistencyConfig#fullConsistencyMaxDelay are two independent params. In order to not produce multiple Connection events this map was added.
  private val eventsEmittedSinceLastCacheRefresh: ConcurrentHashMap[String, EmittedEventType] = new ConcurrentHashMap

  locally {
    participantsCache.registerListener(this).runSyncUnsafe(30.seconds)(scheduler, CanBlock.permit)

    if (healthCheckConfig.healthCheckEnabled) { // if the healthcheck job is disabled, there is no reason to run job MARKING_PARTICIPANTS_AS_EITHER_TURNED_OFF_OR_REMOVED
      backgroundJobsRegistry.addJobAtFixedRate(0.seconds, healthCheckConfig.callsInterval.seconds)(
        () =>
          healthCheckBackgroundJob()
            .onErrorRecover { e: Throwable =>
              logger
                .error(s"Cannot perform healthcheck job on this node. [Node ID: ${nodeTrackerService.getNodeId}]", e)
            },
        "DistributedHealthCheck"
      )

      healthCheckConfig.getMarkedAsNotWorkingJobInterval.foreach { jobInterval =>
        backgroundJobsRegistry.addJobAtFixedRate(jobInterval.seconds, jobInterval.seconds)(
          () =>
            markAsNotWorkingBackgroundJob()
              .onErrorRecover { e: Throwable =>
                logger
                  .error(
                    s"Cannot mark participants as either turned off or removed on this node. [Node ID: ${nodeTrackerService.getNodeId}]",
                    e
                  )
              },
          MARKING_PARTICIPANTS_AS_EITHER_TURNED_OFF_OR_REMOVED_JOB_NAME
        )
      }
    }
  }

  private def healthCheckBackgroundJob(): Task[Unit] =
    Task.eval(logger.debug("Starting DistributedHealthCheckServiceImpl#healthCheckBackgroundJob job")) >>
      Task
        .parZip2(
          getNodesSizeWithCurrentNodePosition,
          participantsCache.getAllWorkingParticipants
        )
        .flatMap {
          case ((Some(nodesSize), Some(currentNodeIndex)), participants) =>
            Task.eval(logger.debug(s"Found $nodesSize nodes - the index of the current node is $currentNodeIndex")) >>
              Task
                .parTraverseUnordered(pickParticipantsForNode(participants, nodesSize, currentNodeIndex)) { participant =>
                  // We are reading eventEmittedSinceLastCacheRefresh as early as possible because there is the scenario
                  // when the health check could take some time and in the meantime, cache got refreshed and
                  // eventsEmittedSinceLastCacheRefresh cleared and as a result, we would insert redundant event - as this is
                  // not the end of the world and we handle this in the proper way, reading this value earlier minimizes the risk.
                  val eventEmittedSinceLastCacheRefresh =
                    Option(eventsEmittedSinceLastCacheRefresh.get(participant.identifier))
                  performHealthcheckCallAndReturnResult(participant)
                    .flatMap {
                      // Healthcheck successful, but the latest participant state inside cache was not healthy, so we are marking participant as healthy one and resetting related failed healthchecks counter.
                      case true
                          if !participant.isHealthy => // we are iterating through working participants only, so the `isHealthy` check is enough
                        unsuccessfulHealthCheckDao
                          .clear(participant.identifier)
                          .map {
                            case Left(error) =>
                              logger.error(
                                s"Unsuccessful healthcheck counter of ${participant.identifier} not cleared because of error."
                              )
                              logger.error(error.getMessage)
                              ()
                            case _ => ()
                          } >> Task
                          .eval(
                            eventEmittedSinceLastCacheRefresh
                              .fold(true)(_ != EmittedEventType.MarkedParticipantAsHealthy)
                          )
                          .flatMap { eventNeedsToBeEmitted =>
                            if (eventNeedsToBeEmitted)
                              participantEventService
                                .markParticipantAsHealthy(participant.identifier)
                                .map { markAsHealthyResult =>
                                  if (markAsHealthyResult.isRight) {
                                    eventsEmittedSinceLastCacheRefresh.put(
                                      participant.identifier,
                                      EmittedEventType.MarkedParticipantAsHealthy
                                    )
                                  }
                                  markAsHealthyResult
                                }
                            else Task.now(Right(()))
                          }
                          .map {
                            case Left(error) =>
                              logger.error(
                                s"${participant.identifier} could not be marked as healthy because of error."
                              )
                              logger.error(error.getMessage)
                              ()
                            case _ => ()
                          }

                      // Healthcheck failed, but the latest participant state inside cache was healthy, so we are incrementing related failed healthchecks counter and if the counter reaches specified limit we mark participant as not healthy.
                      case false if participant.isHealthy =>
                        unsuccessfulHealthCheckDao
                          .incrementCounter(participant.identifier)
                          .map {
                            case Right(counter)
                                if counter >= healthCheckConfig.numberOfFailuresNeededToReact
                                  && eventEmittedSinceLastCacheRefresh.fold(true)(
                                    _ != EmittedEventType.MarkedParticipantAsUnhealthy
                                  ) =>
                              true
                            case Right(_) => false
                            case Left(error) =>
                              logger.error(
                                s"Unsuccessful healthcheck counter of ${participant.identifier} not incremented because of error."
                              )
                              logger.error(error.getMessage)
                              false
                          }
                          .flatMap { eventNeedsToBeEmitted =>
                            if (eventNeedsToBeEmitted)
                              participantEventService
                                .markParticipantAsUnhealthy(participant.identifier)
                                .map { markAsUnhealthyResult =>
                                  if (markAsUnhealthyResult.isRight) {
                                    eventsEmittedSinceLastCacheRefresh.put(
                                      participant.identifier,
                                      EmittedEventType.MarkedParticipantAsUnhealthy
                                    )
                                  }
                                  markAsUnhealthyResult
                                }
                            else Task.now(Right(()))
                          }
                          .map {
                            case Left(error) =>
                              logger.error(
                                s"${participant.identifier} could not be marked as unhealthy because of error."
                              )
                              logger.error(error.getMessage)
                              ()
                            case _ => ()
                          }

                      // 2 scenarios where actions are not needed:
                      //    - healthcheck successful + participant healthy (all good)
                      //    - healthcheck failed + participant not healthy (we already are aware that participant is not healthy, so no event needs to be emitted)
                      case _ => Task.unit
                    }
                }
                .flatMap(_ => Task.unit)
          case ((_, _), _) => Task.unit
        }

  private def markAsNotWorkingBackgroundJob(): Task[Unit] = nodeTrackerService
    .isCurrentNodeResponsibleForJob(MARKING_PARTICIPANTS_AS_EITHER_TURNED_OFF_OR_REMOVED_JOB_NAME)
    .flatMap {
      case true =>
        markAsNotWorkingDeviationInMillis match {
          case Some(deviation) =>
            logger.debug(
              s"Executing the job $MARKING_PARTICIPANTS_AS_EITHER_TURNED_OFF_OR_REMOVED_JOB_NAME, as the node (${nodeTrackerService.getNodeId}) is responsible for it"
            )
            val comparisonStartingPoint: Long = System.currentTimeMillis()

            unsuccessfulHealthCheckDao
              .getStaleEntries(deviation, healthCheckConfig.numberOfFailuresNeededToReact)
              .flatMap { staleEntries =>
                Task
                  .parTraverseUnordered(staleEntries) { staleEntry =>
                    OptionT(Task.now(healthCheckConfig.getParticipantIsMarkedAsRemovedDelayInMillis))
                      .flatMap { markedAsRemovedDelay =>
                        // Firstly, we are trying to determine whether the participant should be marked as removed - if yes, there is no need to check whether should be marked as turned off, as this is one step earlier in the participant life cycle.
                        if (staleEntry.lastUpdateTimestamp <= comparisonStartingPoint - markedAsRemovedDelay) {
                          // Very important is the order of execution. Always send an event with the participantEventService firstly! Sending multiple same events is OK, but clearing the collection too early can cause really bad effects.
                          OptionT(
                            participantEventService
                              .removeParticipant(staleEntry.identifier)
                              .map {
                                case Right(_) => Right(())
                                case Left(NotExists(_)) =>
                                  Right(()) // This is ok! Something else already removed the participant.
                                case Left(error) => logger.error(error.getMessage); Left(error)
                              }
                              .map {
                                case Right(_) =>
                                  Some(MarkAsNotWorkingResult.MarkedAsRemoved(staleEntry.identifier)): Some[
                                    MarkAsNotWorkingResult
                                  ]
                                case Left(_) =>
                                  Option.empty[
                                    MarkAsNotWorkingResult
                                  ] // We cannot clear the unsuccessfulHealthCheck if we are not sure the participant is marked as removed.
                              }
                          )
                        } else OptionT(Task.now(Option.empty[MarkAsNotWorkingResult]))
                      }
                      .orElse {
                        OptionT(Task.now(healthCheckConfig.getParticipantIsMarkedAsTurnedOffDelayInMillis))
                          .flatMap { markedAsTurnedOffDelay =>
                            if (staleEntry.lastUpdateTimestamp <= comparisonStartingPoint - markedAsTurnedOffDelay) {
                              OptionT(
                                participantEventService
                                  .markParticipantAsTurnedOff(staleEntry.identifier)
                                  .map {
                                    case Right(_)           => Right(())
                                    case Left(NotExists(_)) => Right(())
                                    case Left(error)        => logger.error(error.getMessage); Left(error)
                                  }
                                  .map {
                                    case Right(_) =>
                                      healthCheckConfig.getParticipantIsMarkedAsRemovedDelay match {
                                        case Some(_) =>
                                          Some(MarkAsNotWorkingResult.MarkedAsTurnedOff(staleEntry.identifier))
                                        case None =>
                                          Some(
                                            MarkAsNotWorkingResult.MarkedAsRemoved(staleEntry.identifier)
                                          ) // If the IsMarkedAsRemovedDelay is not set, we can mark it as removed straight away (so basically clear away the UnsuccessfulHealthCheck collection) without setting the turnedOff flag, because there is nothing to wait for.
                                      }
                                    case Left(_) =>
                                      Option.empty // We cannot mark as turnedOff inside UnsuccessfulHealthCheck, if we are not sure the participant is marked as turnedOff (the event has been fired).
                                  }
                              )
                            } else OptionT(Task.now(Option.empty))
                          }
                      }
                      .value
                  }
                  .flatMap { entriesToProcess => // Making at most 2 calls to unsuccessfulHealthCheckDao, instead of N
                    Task
                      .eval(entriesToProcess.foldLeft((List.empty[String], List.empty[String])) { (prev, markAsResult) =>
                        markAsResult match {
                          case Some(MarkAsNotWorkingResult.MarkedAsRemoved(identifier)) =>
                            (identifier :: prev._1, prev._2)
                          case Some(MarkAsNotWorkingResult.MarkedAsTurnedOff(identifier)) =>
                            (prev._1, identifier :: prev._2)
                          case None => prev
                        }
                      })
                      .flatMap { data =>
                        val (toRemove, toTurnOff) = data
                        Task.parZip2(
                          if (toRemove.nonEmpty) unsuccessfulHealthCheckDao.clear(toRemove).map {
                            case Right(_)    => ()
                            case Left(error) => logger.error(error.getMessage); ()
                          }
                          else Task.unit,
                          if (toTurnOff.nonEmpty) unsuccessfulHealthCheckDao.markAsTurnedOff(toTurnOff).map {
                            case Right(_)    => ()
                            case Left(error) => logger.error(error.getMessage); ()
                          }
                          else Task.unit
                        )
                      }
                  }
              }
              .void
          case None =>
            logger.debug(
              s"Not executing the job $MARKING_PARTICIPANTS_AS_EITHER_TURNED_OFF_OR_REMOVED_JOB_NAME, as the job is disabled by the configuration"
            )
            Task.unit
        }
      case false =>
        logger.debug(
          s"Not executing the job $MARKING_PARTICIPANTS_AS_EITHER_TURNED_OFF_OR_REMOVED_JOB_NAME, as the node (${nodeTrackerService.getNodeId}) is NOT responsible for it"
        )
        Task.unit
    }

  @VisibleForTesting
  private def getNodesSizeWithCurrentNodePosition: Task[(Option[Int], Option[Int])] =
    nodeTrackerService.getWorkingNodes.flatMap(nodes =>
      Task
        .eval(nodes.map(_._id.toString).indexOf(nodeTrackerService.getNodeId))
        .map {
          case index if index >= 0 => (Some(nodes.size), Some(index))
          case _                   => (Option.empty, Option.empty)
        }
    )

  @VisibleForTesting
  private def pickParticipantsForNode(
      participants: List[Participant],
      nodesSize: Int,
      nodeIndex: Int
  ): List[Participant] =
    participants.filter(p => Math.abs(MurmurHash3.stringHash(p.identifier)) % nodesSize == nodeIndex)

  private def performHealthcheckCallAndReturnResult(participant: Participant): Task[Boolean] =
    client
      .run(
        Request[Task]()
          .withUri(
            Uri(
              authority = Some(
                Authority(
                  host = RegName(participant.host),
                  port = Some(participant.port)
                )
              ),
              path = Path.unsafeFromString(participant.healthcheckInfo.path)
            )
          )
          .withHeaders(
            RequestUtils.withHostHeader(participant.host, participant.port)
          )
      )
      .use(Task.eval(_))
      .map(_.status.isSuccess)
      .onErrorRecoverWith { _ => Task.now(false) }

  // Once the cache is refreshed, we can assume that if the event was inserted it already should be present in the cache.
  // Even if there will be some race (cache refresh is quite expensive) and we will clear too early - this is ok
  // because in the worst scenario there will be a second event inserted (the default logic
  // saves an event if eventsEmittedSinceLastCacheRefresh does not contain the identifier that was asked)
  override def notifyAboutAdd(items: immutable.Iterable[Participant]): Task[Unit] =
    Task.eval(eventsEmittedSinceLastCacheRefresh.clear())

  override def notifyAboutRemove(items: immutable.Iterable[Participant]): Task[Unit] =
    Task.unit // no action needs to be taken
}
