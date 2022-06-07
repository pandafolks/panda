package com.github.mattszm.panda.routes

import com.github.mattszm.panda.routes.RoutesTree.{Fixed, Node, Wildcard}
import com.github.mattszm.panda.routes.dto.RoutesMappingInitDto
import org.http4s.Uri
import org.http4s.Uri.Path

final class RoutesTreeImpl(private val root: Node) extends RoutesTree {

  override def getRoot: Node = root.copy()

  override def specifyGroup(path: Path): Option[GroupInfo] = {
    def rc(curr: Node, segments: List[Uri.Path.Segment]): Option[GroupInfo] = {
      if (segments.isEmpty) curr.groupInfo
      else {
        val (fixed, wildcard): (Vector[Node], Option[Node]) = curr.children.lastOption match {
          case None => (Vector.empty, Option.empty)
          case Some(node) if node.value == Wildcard => (curr.children.tail, Some(node))
          case Some(_) => (curr.children, Option.empty)
        }

        fixed.binarySearch(segments.head.encoded).orElse(wildcard).flatMap(rc(_, segments.tail))
      }
    }

    rc(root, path.segments.toList)
  }

  implicit class NodeVectorOps(nodes: Vector[Node]) {
    @scala.annotation.tailrec
    private def bs(nodes: Vector[Node], left: Int, right: Int, expr: String): Option[Node] = {
      if (right < left) Option.empty
      else {
        val middle = (left + right) / 2
        val midElement = nodes(middle)
        val midValue = midElement.value match {
          case Fixed(expression) => expression
          case Wildcard => "" // the wildcard should never be here
        }

        if (midValue == expr) Some(midElement)
        else {
          if (expr.compareTo(midValue) > 0) bs(nodes, middle + 1, right, expr)
          else bs(nodes, left, middle - 1, expr)
        }
      }
    }

    def binarySearch(expr: String): Option[Node] = bs(nodes, 0, nodes.length - 1, expr)
  }

}

object RoutesTreeImpl {

  def unifyPrefixesAndConstruct(data: RoutesMappingInitDto, httpMethod: HttpMethod = HttpMethod.Get): RoutesTree =
    construct(data.withUnifiedPrefixes, httpMethod)

  def construct(data: RoutesMappingInitDto, httpMethod: HttpMethod = HttpMethod.Get): RoutesTree =
    new RoutesTreeImpl(
      data.get(httpMethod).iterator
        .foldLeft(Node(RoutesTree.Wildcard, Vector.empty))((root, entry) =>
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
        val affectedNode = currentNode.children.find(_.value == parts.head).getOrElse(Node(parts.head, Vector.empty))
        currentNode.copy(
          children = (rc(parts.tail, affectedNode) +: currentNode.children.filterNot(_.value == parts.head))
            .sorted(RoutesTree.orderingByValueType)
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
