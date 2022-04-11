package com.mattszm.panda.routes

import com.mattszm.panda.routes.RoutesTree.Node
import com.mattszm.panda.routes.dto.RoutesMappingInitializationDto

final class RoutesTreeImpl(private val root: Node) extends RoutesTree {

  override def getRoot: Node = root.copy()

  override def specifyGroup(path: String): GroupInfo = ???
}

object RoutesTreeImpl {
  def construct(data: RoutesMappingInitializationDto): RoutesTreeImpl = {
    val dataWithProcessedPrefixes = data.copy(
      prefixes = data.prefixes.view.mapValues(
        _.dropWhile(_ == '/').reverse.dropWhile(_ == '/').reverse
      ).toMap
    )

    new RoutesTreeImpl(
      dataWithProcessedPrefixes.mappers.iterator
        .foldLeft(Node(RoutesTree.Wildcard, List.empty))((root, entry) =>
          insert(
            root = root,
            path = entry._1,
            info = GroupInfo(Group(entry._2), dataWithProcessedPrefixes.prefixes.getOrElse(entry._2, ""))
          )
        )
    )
  }

  private def insert(root: Node, path: String, info: GroupInfo): Node = {
    def rc(parts: List[RoutesTree.Value], currentNode: Node): Node = {
      if (parts.isEmpty) currentNode.copy(groupInfo = Some(info))
      else {
        val affectedNode = currentNode.children.find(_.value == parts.head).getOrElse(Node(parts.head, List.empty))
        currentNode.copy(
          children = rc(parts.tail, affectedNode) :: currentNode.children.filterNot(_.value == parts.head)
        )
      }
    }

    val parts: List[RoutesTree.Value] = path.split("/").filter(_ != "").toList.map {
      case entry if entry.startsWith("{{") && entry.endsWith("}}") => RoutesTree.Wildcard
      case entry => RoutesTree.Fixed(entry)
    }
    rc(parts, root)
  }
}
