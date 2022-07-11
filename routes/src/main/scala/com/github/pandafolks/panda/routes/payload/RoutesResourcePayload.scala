package com.github.pandafolks.panda.routes.payload

final case class RoutesResourcePayload(
                                        mappers: Option[Map[String, MapperRecordPayload]] = Option.empty,
                                        prefixes: Option[Map[String, String]] = Option.empty
                                      )

final case class MapperRecordPayload(mapping: MappingPayload, method: Option[String] = Option.empty)

final case class MappingPayload(value: Either[String, Map[String, MappingPayload]])
