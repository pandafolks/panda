package com.mattszm.panda.routes

import com.mattszm.panda.routes.dto.RoutesMappingInitializationDto


final class RoutesTree {
  import com.mattszm.panda.routes.RoutesTree.{Fixed, GroupInfo, Node, Wildcard, Value}

  private var head: Node = Node(Wildcard, List.empty)

  def insert(path: String, info: GroupInfo): Unit = {
    def rc(parts: List[Value], currentNode: Node): Node = {
      if (parts.isEmpty) currentNode.copy(groupInfo = Some(info))
      else {
        val affectedNode = currentNode.children.find(_.value == parts.head).getOrElse(Node(parts.head, List.empty))
        currentNode.copy(
          children = rc(parts.tail, affectedNode) :: currentNode.children.filterNot(_.value == parts.head)
        )
      }
    }

    val parts: List[Value] = path.split("/").filter(_ != "").toList.map {
      case entry if entry.startsWith("{{") && entry.endsWith("}}") => Wildcard
      case entry => Fixed(entry)
    }
    head = rc(parts, head)
  }

  private def this(data: RoutesMappingInitializationDto) = {
    this()
    val dataWithProcessedPrefixes = data.copy(
      prefixes = data.prefixes.view.mapValues(
        _.dropWhile(_ == '/').reverse.dropWhile(_ == '/').reverse
      ).toMap
    )

    dataWithProcessedPrefixes.mappers.iterator.foreach(entry => insert(
      path = entry._1,
      info = GroupInfo(Group(entry._2), dataWithProcessedPrefixes.prefixes.getOrElse(entry._2, ""))
    ))
  }

  def getHead: Node = head.copy()
}

object RoutesTree {
  sealed trait Value
  final case class Fixed(expression: String) extends Value
  final case object Wildcard extends Value

  final case class GroupInfo(group: Group, prefix: String)
  final case class Node(value: Value, children: List[Node], groupInfo: Option[GroupInfo] = Option.empty)

  def construct(data: RoutesMappingInitializationDto): RoutesTree = new RoutesTree(data)
}

