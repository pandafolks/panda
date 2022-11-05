package com.github.pandafolks.panda.routes

import RoutesTree.{Fixed, Node, RouteInfo, Wildcard}
import com.github.pandafolks.panda.routes.entity.Mapper
import org.http4s.Uri
import org.http4s.Uri.Path

final class RoutesTreeImpl(private val root: Node) extends RoutesTree {

  override def getRoot: Node = root.copy()

  override def find(path: Path, standaloneOnly: Boolean = false): Option[(RouteInfo, Map[String, String])] = {
    // Searching priority:
    //  - Fixed
    //  - Wildcard
    //  - Pocket
    def rc(curr: Node, segments: List[Uri.Path.Segment]): Option[(RouteInfo, Map[String, String])] = {
      if (segments.isEmpty) curr.routeInfo.map((_, Map.empty[String, String]))
      else
        curr.children
          .find(node =>
            node.value match {
              case Fixed(expression) if expression == segments.head.encoded => true
              case _: Fixed                                                 => false
              case _: Wildcard                                              => true
            }
          )
          .flatMap(foundNode =>
            rc(foundNode, segments.tail)
              .map(res =>
                foundNode.value match {
                  case Wildcard(expression) => (res._1, res._2 + (expression -> segments.head.encoded))
                  case _                    => res
                }
              )
          )
          .orElse(curr.routeInfo.filter(_.isPocket).map((_, Map.empty[String, String])))
    }

    // either standaloneOnly is false and we accept any result or standaloneOnly is required so the result needs to be standalone one.
    rc(root, path.segments.toList).filter(result => (standaloneOnly && result._1.isStandalone) || !standaloneOnly)
  }
}

object RoutesTreeImpl {

  def construct(data: List[Mapper]): RoutesTree =
    new RoutesTreeImpl(
      data
        .foldLeft(Node(RoutesTree.Wildcard(), List.empty))((root, entry) =>
          insert(
            root = root,
            path = entry.route,
            routeInfo = RoutesTree.RouteInfo(
              entry.mappingContent,
              isStandalone = entry.isStandalone
            )
          )
        )
    )

  private def insert(root: Node, path: String, routeInfo: RoutesTree.RouteInfo): Node = {

    def rc(parts: List[RoutesTree.SegmentType], currentNode: Node): Node =
      parts.headOption match {
        case None                    => currentNode.copy(routeInfo = Some(routeInfo))
        case Some(RoutesTree.Pocket) => currentNode.copy(routeInfo = Some(routeInfo.copy(isPocket = true)))
        case Some(head: RoutesTree.Value) =>
          val affectedNode = currentNode.children.find(_.value == head).getOrElse(Node(head, List.empty))
          currentNode.copy(
            children = (rc(parts.tail, affectedNode) :: currentNode.children.filterNot(_.value == head)).sorted
          )
      }

    def splitIntoParts(path: String)(mapFunc: String => RoutesTree.SegmentType): List[RoutesTree.SegmentType] =
      path.split("/").filterNot(_.isBlank).map(mapFunc).toList

    val mainParts: List[RoutesTree.SegmentType] = splitIntoParts(path) {
      case "**"          => RoutesTree.Pocket
      case s"{{$entry}}" => RoutesTree.Wildcard(entry)
      case entry         => RoutesTree.Fixed(entry)
    }

    rc(mainParts, root)
  }
}
