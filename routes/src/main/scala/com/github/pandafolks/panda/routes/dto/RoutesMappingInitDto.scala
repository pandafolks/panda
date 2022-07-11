package com.github.pandafolks.panda.routes.dto

import com.github.pandafolks.panda.routes.HttpMethod
import ujson.Value

import scala.collection.immutable.ListMap

final case class RoutesMappingInitDto(
                                       prefixes: Map[String, String] = Map.empty,
                                       getMappers: Map[String, String] = Map.empty,
                                       postMappers: Map[String, String] = Map.empty
                                     ) {

  def get(httpMethod: HttpMethod): Map[String, String] = httpMethod match {
    case _: HttpMethod.Get.type => getMappers
    case _: HttpMethod.Post.type  => postMappers

    case _ => getMappers
  }

  def withUnifiedPrefixes: RoutesMappingInitDto =
    this.copy(prefixes = this.prefixes.view.mapValues(_.dropWhile(_ == '/').reverse.dropWhile(_ == '/').reverse).toMap) // todo: mszmal add this during saving prefixes via rest - just as preprocessing
}

object RoutesMappingInitDto {
  private final val PREFIXES_NAME = "prefixes"
  private final val GET_MAPPERS_NAME = "getMappers"
  private final val POST_MAPPERS_NAME = "postMappers"

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
      extractMapFromConfiguration(PREFIXES_NAME),
      extractMapFromConfiguration(GET_MAPPERS_NAME),
      extractMapFromConfiguration(POST_MAPPERS_NAME)
    )
  }
}
