package com.github.mattszm.panda.loadbalancer

import com.github.mattszm.panda.routes.Group

import java.util.concurrent.ConcurrentHashMap
import scala.collection.immutable.TreeMap

final class ConsistentHashingState(private val positionsPerIdentifier: Int = 100) {
  // add a background job that will clear empty groups. It's more efficient to keep unused groups for a while
  // than check on every remove (cannot be performed in O(1))

  private val usedPositionsGroupedByGroup: ConcurrentHashMap[Group, TreeMap[Int, String]] = new ConcurrentHashMap
  private val usedIdentifiersWithPositions: ConcurrentHashMap[String, List[Int]] = new ConcurrentHashMap // identifiers are unique across all groups
  private val random = scala.util.Random

  def get(group: Group, requestedPosition: Int): Option[String] =
    Option(usedPositionsGroupedByGroup.get(group)).flatMap {
      case positions if positions.isEmpty => Option.empty
      case positions => Some(positions.minAfter(requestedPosition).getOrElse(positions.min)._2)
    }

  def add(group: Group, participantIdentifier: String): Unit =
    usedIdentifiersWithPositions.computeIfAbsent(participantIdentifier, _ => {
      var positions = List.empty[Int]
      usedPositionsGroupedByGroup.compute(group, (_, v) => {
        var tree = Option(v).getOrElse(TreeMap.empty)
        var i = 0
        while (i < positionsPerIdentifier) {
          val shot = random.nextInt(Integer.MAX_VALUE)
          if (!tree.contains(shot)) {
            tree = tree + (shot, participantIdentifier)
            positions = shot :: positions
            i += 1
          }
        }
        tree
      })
      positions // initialized only once in a lifetime - no updates
    })

  def remove(group: Group, participantIdentifier: String): Unit =
    Option(usedIdentifiersWithPositions.remove(participantIdentifier))
      .foreach(identifierPositions => usedPositionsGroupedByGroup.computeIfPresent(group, (_, tree) => // if there is an entry, there has to be a tree (at least empty)
        identifierPositions.foldLeft(tree)((prevTree, identifierPosition) => prevTree - identifierPosition)
      ))
}
