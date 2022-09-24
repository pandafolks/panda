package com.github.pandafolks.panda.routes.payload

final case class RoutesResourcePayload(
                                        mappers: Option[List[(String, MapperRecordPayload)]] = Option.empty,
                                        prefixes: Option[Map[String, String]] = Option.empty
                                      )

final case class MapperRecordPayload(
                                      mapping: MappingPayload,
                                      method: Option[String] = Option.empty,
                                      isStandalone: Option[Boolean] = Option.empty
                                    ) {
  def isStandaloneOrDefault: Boolean = isStandalone.getOrElse(true)
}

final case class MappingPayload(value: Either[String, Map[String, MappingPayload]])
