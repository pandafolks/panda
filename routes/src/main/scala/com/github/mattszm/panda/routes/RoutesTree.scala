package com.github.mattszm.panda.routes

import RoutesTree.Node
import org.http4s.Uri.Path

trait RoutesTree {
  def getRoot: Node

  def specifyGroup(path: Path): Option[GroupInfo]
}

object RoutesTree {

  sealed trait Value
  final case class Fixed(expression: String) extends Value
  final case object Wildcard extends Value

  final case class Node(value: Value, children: Vector[Node], groupInfo: Option[GroupInfo] = Option.empty)
  implicit val orderingByValueType: Ordering[Node] = Ordering.by { _.value match {
      // Wildcard will be always at the end, whereas Fixed are sorted by expression.
    case Fixed(expression) => (0, expression)
    case Wildcard => (1, "")
  }}
}
