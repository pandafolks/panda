package com.github.pandafolks.panda.routes.dto

final case class RoutesResourceDto(
                                    mappers: Option[Map[String, MapperRecordDto]] = Option.empty,
                                    prefixes: Option[Map[String, String]] = Option.empty
                                  )

final case class MapperRecordDto(groupName: String, method: Option[String] = Option.empty)
