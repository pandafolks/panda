package com.github.pandafolks.panda.routes

import org.http4s.Method

final case class RoutesTreesHandler(
                                     getTree: RoutesTree,
                                     postTree: RoutesTree,
                                     putTree: RoutesTree,
                                     patchTree: RoutesTree,
                                     deleteTree: RoutesTree,
                                   ) {
  def get(method: Method): Option[RoutesTree] =
    method match {
      case Method.GET       => Some(getTree)
      case Method.POST      => Some(postTree)
      case Method.PUT       => Some(putTree)
      case Method.PATCH     => Some(deleteTree)
      case _                => Option.empty
    }
}

object RoutesTreesHandler {
  def apply(): RoutesTreesHandler = new RoutesTreesHandler(null, null, null, null, null)
}
