package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.entity.{Mapper, Prefix}
import org.http4s.Method

final case class RoutesTreesHandler(
                                     getTree: RoutesTree,
                                     postTree: RoutesTree,
                                     putTree: RoutesTree,
                                     patchTree: RoutesTree,
                                     deleteTree: RoutesTree,
                                     prefixes: Map[String, String]
                                   ) {
  def getTree(method: Method): Option[RoutesTree] =
    method match {
      case Method.GET => Some(getTree)
      case Method.POST => Some(postTree)
      case Method.PUT => Some(putTree)
      case Method.PATCH => Some(patchTree)
      case Method.DELETE => Some(deleteTree)
      case _ => Option.empty
    }

  val supportedMethods: List[Method] = List(Method.GET, Method.POST, Method.PUT, Method.PATCH, Method.DELETE)

}

object RoutesTreesHandler {
  def construct(mappers: Map[HttpMethod, List[Mapper]] = Map.empty,
                prefixes: Map[String, Prefix] = Map.empty): RoutesTreesHandler =
    new RoutesTreesHandler(
      getTree = RoutesTreeImpl.construct(mappers.getOrElse(HttpMethod.Get(), List.empty)),
      postTree = RoutesTreeImpl.construct(mappers.getOrElse(HttpMethod.Post(), List.empty)),
      putTree = RoutesTreeImpl.construct(mappers.getOrElse(HttpMethod.Put(), List.empty)),
      patchTree = RoutesTreeImpl.construct(mappers.getOrElse(HttpMethod.Patch(), List.empty)),
      deleteTree = RoutesTreeImpl.construct(mappers.getOrElse(HttpMethod.Delete(), List.empty)),
      prefixes = prefixes.view.mapValues(_.value).toMap
    )

  def withNewMappers(handler: RoutesTreesHandler, mappers: Map[HttpMethod, List[Mapper]] = Map.empty): RoutesTreesHandler =
    handler.copy(
      getTree = RoutesTreeImpl.construct(mappers.getOrElse(HttpMethod.Get(), List.empty)),
      postTree = RoutesTreeImpl.construct(mappers.getOrElse(HttpMethod.Post(), List.empty)),
      putTree = RoutesTreeImpl.construct(mappers.getOrElse(HttpMethod.Put(), List.empty)),
      patchTree = RoutesTreeImpl.construct(mappers.getOrElse(HttpMethod.Patch(), List.empty)),
      deleteTree = RoutesTreeImpl.construct(mappers.getOrElse(HttpMethod.Delete(), List.empty)),
    )

  def withNewPrefixes(handler: RoutesTreesHandler, prefixes: Map[String, Prefix] = Map.empty): RoutesTreesHandler =
    handler.copy(prefixes = prefixes.view.mapValues(_.value).toMap)
}
