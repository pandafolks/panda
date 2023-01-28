package com.github.pandafolks.panda.routes

import RoutesTree.{Node, RouteInfo}
import com.github.pandafolks.panda.routes.entity.MappingContent
import org.http4s.Uri.Path

trait RoutesTree {

  /** Returns the root of the tree.
    *
    * @return
    *   a root node
    */
  def getRoot: Node

  /** Finds the route info based on the request path and the being standalone requirement.
    *
    * @param path
    *   requested route that will be looked for
    * @param standaloneOnly
    *   whether only standalone routes should be looked for
    * @return
    *   Route info together with wildcards mappings if exists, empty otherwise
    */
  def find(path: Path, standaloneOnly: Boolean = false): Option[(RouteInfo, Map[String, String])]
}

object RoutesTree {

  sealed trait SegmentType

  final case object Pocket extends SegmentType

  sealed trait Value extends SegmentType

  final case class Fixed(expression: String) extends Value

  final case class Wildcard(expression: String = "") extends Value

  final case class RouteInfo(
      mappingContent: MappingContent,
      // Pocket Example: "cars/supercars/**" will catch "cars/supercars/ferrari", "cars/supercars/audi/r8", etc.
      isPocket: Boolean = false,
      // Standalone means that the route can be achieved directly from the initial gateway request, not-standalone routes can be only achieved from composition mappings.
      isStandalone: Boolean = true
  )

  final case class Node(value: Value, children: List[Node], routeInfo: Option[RouteInfo] = Option.empty)

  implicit val orderingByValueType: Ordering[Node] = Ordering.by {
    _.value match {
      case _: Fixed    => 0
      case _: Wildcard => 1
    }
  }
}
