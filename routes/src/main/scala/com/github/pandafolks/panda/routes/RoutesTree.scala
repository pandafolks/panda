package com.github.pandafolks.panda.routes

import RoutesTree.Node
import org.http4s.Uri.Path

trait RoutesTree {
  def getRoot: Node

  def specifyGroup(path: Path): Option[GroupInfo]
}

object RoutesTree {

  sealed trait ValueType

  final case object Pocket extends ValueType

  sealed trait Value extends ValueType

  final case class Fixed(expression: String) extends Value
  final case object Wildcard extends Value

  final case class Node(value: Value, children: List[Node], groupInfo: Option[GroupInfo] = Option.empty)

  implicit val orderingByValueType: Ordering[Node] = Ordering.by {
    _.value match {
      case _: Fixed => 0
      case Wildcard => 1
    }
  }
}
