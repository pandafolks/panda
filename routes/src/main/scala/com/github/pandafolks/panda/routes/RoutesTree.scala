package com.github.pandafolks.panda.routes

import RoutesTree.Node
import org.http4s.Uri.Path

trait RoutesTree {
  def getRoot: Node

  def specifyGroup(path: Path, standaloneOnly: Boolean = false): Option[(RouteInfo, Map[String, String])]
}

object RoutesTree {

  sealed trait SegmentType

  final case object Pocket extends SegmentType

  sealed trait Value extends SegmentType

  final case class Fixed(expression: String) extends Value
  final case class Wildcard(expression: String = "") extends Value

  final case class Node(value: Value, children: List[Node], routeInfo: Option[RouteInfo] = Option.empty)

  implicit val orderingByValueType: Ordering[Node] = Ordering.by {
    _.value match {
      case _: Fixed => 0
      case _: Wildcard => 1
    }
  }
}
