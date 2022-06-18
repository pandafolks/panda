package com.github.pandafolks.panda.routes

import RoutesTree.{Fixed, Node, Wildcard}
import com.github.pandafolks.panda.routes.dto.RoutesMappingInitDto
import org.http4s.Uri
import org.http4s.Uri.Path

final class RoutesTreeImpl(private val root: Node) extends RoutesTree {

  override def getRoot: Node = root.copy()

  override def specifyGroup(path: Path): Option[GroupInfo] = {
    def rc(curr: Node, segments: List[Uri.Path.Segment]): Option[GroupInfo] = {
      if (segments.isEmpty) curr.groupInfo
      else curr.children.find(node => node.value match {
        case Fixed(expression) if expression == segments.head.encoded => true
        case _: Fixed => false
        case Wildcard => true
      }).flatMap(rc(_, segments.tail))
    }

    rc(root, path.segments.toList)
  }
}

object RoutesTreeImpl {

  def unifyPrefixesAndConstruct(data: RoutesMappingInitDto, httpMethod: HttpMethod = HttpMethod.Get): RoutesTree =
    construct(data.withUnifiedPrefixes, httpMethod)

  def construct(data: RoutesMappingInitDto, httpMethod: HttpMethod = HttpMethod.Get): RoutesTree =
    new RoutesTreeImpl(
      data.get(httpMethod).iterator
        .foldLeft(Node(RoutesTree.Wildcard, List.empty))((root, entry) =>
          insert(
            root = root,
            path = entry._1,
            info = GroupInfo(
              Group(entry._2),
              Path.unsafeFromString(data.prefixes.getOrElse(entry._2, ""))
            )
          )
        )
    )

  private def insert(root: Node, path: String, info: GroupInfo): Node = {

    def rc(parts: List[RoutesTree.Value], currentNode: Node): Node = {
      if (parts.isEmpty) currentNode.copy(groupInfo = Some(info))
      else {
        val affectedNode = currentNode.children.find(_.value == parts.head).getOrElse(Node(parts.head, List.empty))
        currentNode.copy(
          children = (rc(parts.tail, affectedNode) :: currentNode.children.filterNot(_.value == parts.head)).sorted
        )
      }
    }

    def splitIntoParts(path: String)(mapFunc: String => RoutesTree.Value): List[RoutesTree.Value] =
      path.split("/").filterNot(_ == "").map(mapFunc).toList

    val mainParts: List[RoutesTree.Value] = splitIntoParts(path) {
      case entry if entry.startsWith("{{") && entry.endsWith("}}") => RoutesTree.Wildcard
      case entry => RoutesTree.Fixed(entry)
    }

    rc(mainParts, root)
  }
}
