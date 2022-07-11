package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.dto.RoutesMappingInitDto
import org.http4s.Method

final case class RoutesTrees(get: RoutesTree, post: RoutesTree) {
  def get(method: Method): RoutesTree =
    method match {
      case Method.GET => get
      case Method.POST => post
      case _ => ??? //todI: mszmal finish -> this should return optional???
    }
}

object RoutesTrees {
  def construct(data: RoutesMappingInitDto): RoutesTrees = {
    val dataWithUnifiedPrefixes = data.withUnifiedPrefixes

    RoutesTrees(
      get = RoutesTreeImpl.construct(dataWithUnifiedPrefixes, HttpMethod.Get),
      post = RoutesTreeImpl.construct(dataWithUnifiedPrefixes, HttpMethod.Post)
    )

  }
}
