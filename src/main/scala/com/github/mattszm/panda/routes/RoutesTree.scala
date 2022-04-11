package com.github.mattszm.panda.routes

import RoutesTree.Node

trait RoutesTree {
  def getRoot: Node

  def specifyGroup(path: String): GroupInfo
}

object RoutesTree {

  sealed trait Value
  final case class Fixed(expression: String) extends Value
  final case object Wildcard extends Value

  final case class Node(value: Value, children: List[Node], groupInfo: Option[GroupInfo] = Option.empty)

}
