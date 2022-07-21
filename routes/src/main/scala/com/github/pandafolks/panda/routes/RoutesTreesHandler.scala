package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.entity.Mapper
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
  def construct(data: Map[HttpMethod, List[Mapper]]): RoutesTreesHandler = {
    new RoutesTreesHandler(
      RoutesTreeImpl.construct(data.getOrElse(HttpMethod.Get(), List.empty)),
      RoutesTreeImpl.construct(data.getOrElse(HttpMethod.Post(), List.empty)),
      RoutesTreeImpl.construct(data.getOrElse(HttpMethod.Put(), List.empty)),
      RoutesTreeImpl.construct(data.getOrElse(HttpMethod.Patch(), List.empty)),
      RoutesTreeImpl.construct(data.getOrElse(HttpMethod.Delete(), List.empty)),
    )
  }
}
