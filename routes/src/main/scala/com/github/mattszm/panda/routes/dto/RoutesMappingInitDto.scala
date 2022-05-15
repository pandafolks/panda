package com.github.mattszm.panda.routes.dto

import ujson.Value

import scala.collection.immutable.ListMap

final case class RoutesMappingInitDto(
                                       mappers: Map[String, String],
                                       prefixes: Map[String, String]
                                     )

object RoutesMappingInitDto {
  private final val MAPPERS_NAME = "mappers"
  private final val PREFIXES_NAME = "prefixes"

  def of(configuration: Value.Value): RoutesMappingInitDto = {
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

    RoutesMappingInitDto(
      extractMapFromConfiguration(MAPPERS_NAME),
      extractMapFromConfiguration(PREFIXES_NAME)
    )
  }
}
