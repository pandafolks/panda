package com.github.pandafolks.panda.loadbalancer

import com.github.pandafolks.panda.backgroundjobsregistry.BackgroundJobsRegistry
import com.github.pandafolks.panda.participant.Participant
import com.github.pandafolks.panda.routes.Group
import com.github.pandafolks.panda.utils.SystemProperties
import com.github.pandafolks.panda.utils.listener.QueueBasedChangeListener
import com.google.common.annotations.VisibleForTesting
import monix.eval.Task
import org.slf4j.{Logger, LoggerFactory}

import java.util.concurrent.ConcurrentHashMap
import scala.collection.immutable.TreeMap
import scala.concurrent.duration.DurationInt
import scala.util.Random

final class ConsistentHashingState(
    private val backgroundJobsRegistry: BackgroundJobsRegistry
)(
    private val positionsPerParticipant: Int = SystemProperties.consistentHashingStatePositionsPerParticipant,
    private val clearEmptyGroupsIntervalInHours: Int = SystemProperties.consistentHashingStateClearEmptyGroupsIntervalInHours
) extends QueueBasedChangeListener[Participant] {
  private val random = new Random(System.currentTimeMillis())

  private val logger: Logger = LoggerFactory.getLogger(getClass.getName)

  @VisibleForTesting
  private val usedPositionsGroupedByGroup: ConcurrentHashMap[Group, TreeMap[Int, Participant]] = new ConcurrentHashMap
  @VisibleForTesting
  private val usedParticipantsWithPositions: ConcurrentHashMap[Participant, List[Int]] =
    new ConcurrentHashMap // Participants are equal only if all their properties are equal

  locally {
    if (clearEmptyGroupsIntervalInHours > 0) {
      backgroundJobsRegistry.addJobAtFixedRate(
        clearEmptyGroupsIntervalInHours.hours,
        clearEmptyGroupsIntervalInHours.hours
      )(
        () =>
          clearEmptyGroups()
            .onErrorRecover { e: Throwable => logger.error(s"Cannot clear ${getClass.getName} empty groups.", e) },
        "ConsistentHashingStateClearEmptyGroups"
      )
    }
  }

  def get(group: Group, requestedPosition: Int): Option[Participant] =
    Option(usedPositionsGroupedByGroup.get(group)).flatMap {
      case positions if positions.isEmpty => Option.empty
      case positions                      => Some(positions.minAfter(requestedPosition).getOrElse(positions.head)._2)
    }

  def add(participant: Participant): Unit = {
    logger.debug(s"Adding the participant [${participant.identifier}] to the state.")

    usedParticipantsWithPositions.computeIfAbsent(
      participant,
      _ => { // safe if there is already a requested participant, there won't be duplicates
        var positions = List.empty[Int]
        usedPositionsGroupedByGroup.compute(
          participant.group,
          (_, v) => {
            var tree = Option(v).getOrElse(TreeMap.empty[Int, Participant])
            var i = 0
            while (i < positionsPerParticipant) {
              val shot = random.nextInt(Integer.MAX_VALUE)
              if (!tree.contains(shot)) {
                tree = tree.updated(shot, participant)
                positions = shot :: positions
                i += 1
              }
            }
            tree
          }
        )
        positions // initialized only once in a lifetime - no updates
      }
    )
    ()
  }

  def remove(participant: Participant): Unit = {
    logger.debug(s"Removing the participant [${participant.identifier}] from the state.")

    Option(usedParticipantsWithPositions.remove(participant)) // safe if there is no participant
      .foreach(participantPositions =>
        usedPositionsGroupedByGroup.computeIfPresent(
          participant.group,
          (_, tree) => // if there is an entry, there has to be a tree (at least empty)
            participantPositions.foldLeft(tree)((prevTree, participantPosition) => prevTree - participantPosition)
        )
      )
  }

  override protected def notifyAboutAddInternal(item: Participant): Task[Unit] =
    if (item.isWorkingAndHealthy) Task.eval(add(item)) else Task.eval(remove(item))
  // ConsistentHashingState should track only working and healthy participants (it is corresponding to getHealthyParticipantsAssociatedWithGroup)

  override protected def notifyAboutRemoveInternal(item: Participant): Task[Unit] = Task.eval(remove(item))

  private def clearEmptyGroups(): Task[Unit] = {
    Task.eval(logger.debug("Clearing empty groups of ConsistentHashingState#usedPositionsGroupedByGroup")) >>
      Task.eval(
        usedPositionsGroupedByGroup
          .keySet()
          .forEach(g => {
            usedPositionsGroupedByGroup.remove(g, TreeMap.empty[Int, Participant])
            ()
          })
      )
  }
}
