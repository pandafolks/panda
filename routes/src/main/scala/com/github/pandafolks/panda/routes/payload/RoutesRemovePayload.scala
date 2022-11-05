package com.github.pandafolks.panda.routes.payload

final case class RoutesRemovePayload(
    mappers: Option[List[MapperRemovePayload]] = Option.empty,
    prefixes: Option[List[String]] = Option.empty
)

final case class MapperRemovePayload(route: String, method: Option[String] = Option.empty)
