package com.mattszm.panda.gateway.dto

import ujson.Value

import scala.collection.immutable.ListMap

final case class GatewayMappingInitializationDto(
                                                mappers: Map[String, String],
                                                prefixes: Map[String, String]
                                                )

object GatewayMappingInitializationDto {
  private final val MAPPERS_NAME = "mappers"
  private final val PREFIXES_NAME = "prefixes"

  def of(configuration: Value.Value): GatewayMappingInitializationDto = {
    def extractMapFromConfiguration(propertyName: String): Map[String, String] =
      configuration.objOpt
        .flatMap(mutableMap => mutableMap.get(propertyName))
        .flatMap(_.objOpt)
        .map(Map.from(_))
        .getOrElse(ListMap.empty)
        .view.mapValues(_.strOpt)
        .filter(_._2.isDefined)
        .mapValues(_.get)
        .toMap

    GatewayMappingInitializationDto(
      extractMapFromConfiguration(MAPPERS_NAME),
      extractMapFromConfiguration(PREFIXES_NAME)
    )
  }
}
