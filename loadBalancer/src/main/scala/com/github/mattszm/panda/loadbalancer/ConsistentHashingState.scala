package com.github.mattszm.panda.loadbalancer

import com.github.mattszm.panda.participant.{Participant, Working}
import com.github.mattszm.panda.routes.Group
import com.github.mattszm.panda.utils.ChangeListener
import com.google.common.annotations.VisibleForTesting
import monix.eval.Task

import java.util.concurrent.ConcurrentHashMap
import scala.collection.immutable.TreeMap
import scala.util.Random

final class ConsistentHashingState(private val positionsPerIdentifier: Int = 100) extends ChangeListener[Participant] {
  // add a background job that will clear empty groups. It's more efficient to keep unused groups for a while
  // than check on every remove (cannot be performed in O(1))
  // ConsistentHashingState should keep only WORKING participants

  @VisibleForTesting
  private val usedPositionsGroupedByGroup: ConcurrentHashMap[Group, TreeMap[Int, Participant]] = new ConcurrentHashMap
  @VisibleForTesting
  private val usedIdentifiersWithPositions: ConcurrentHashMap[Participant, List[Int]] = new ConcurrentHashMap // identifiers are unique across all groups
  private val random = new Random(System.currentTimeMillis())

  def get(group: Group, requestedPosition: Int): Option[Participant] =
    Option(usedPositionsGroupedByGroup.get(group)).flatMap {
      case positions if positions.isEmpty => Option.empty
      case positions => Some(positions.minAfter(requestedPosition).getOrElse(positions.head)._2)
    }

  def add(participant: Participant): Unit = {
    usedIdentifiersWithPositions.computeIfAbsent(participant, _ => {
      var positions = List.empty[Int]
      usedPositionsGroupedByGroup.compute(participant.group, (_, v) => {
        var tree = Option(v).getOrElse(TreeMap.empty[Int, Participant])
        var i = 0
        while (i < positionsPerIdentifier) {
          val shot = random.nextInt(Integer.MAX_VALUE)
          if (!tree.contains(shot)) {
            tree = tree.updated(shot, participant)
            positions = shot :: positions
            i += 1
          }
        }
        tree
      })
      positions // initialized only once in a lifetime - no updates
    })
    ()
  }

  def remove(participant: Participant): Unit =
    Option(usedIdentifiersWithPositions.remove(participant))
      .foreach(identifierPositions => usedPositionsGroupedByGroup.computeIfPresent(participant.group, (_, tree) => // if there is an entry, there has to be a tree (at least empty)
        identifierPositions.foldLeft(tree)((prevTree, identifierPosition) => prevTree - identifierPosition)
      ))

  override def notifyAboutAdd(items: List[Participant]): Task[Unit] =
    Task.parTraverse(items)(item => if (item.status == Working) Task.eval(add(item)) else Task.eval(remove(item))).void

  override def notifyAboutRemove(items: List[Participant]): Task[Unit] =
    Task.parTraverse(items)(item => Task.eval(remove(item))).void
}
