package com.github.pandafolks.panda.routes.mappers

import com.github.pandafolks.panda.routes.dto.MappingDto


final case class MappingContent(left: Option[String], right: Option[Map[String, MappingContent]]) // mongo cannot handle `Either`s

object MappingContent {
  def fromMappingDto(mapping: MappingDto): MappingContent = {
    def rc(mapping: MappingDto): MappingContent =
      mapping.value match {
        case Left(v) => MappingContent(left = Some(v), right = Option.empty)
        case Right(map) => MappingContent(
          left = Option.empty,
          right = Some(
            map.iterator.foldLeft(Map.empty[String, MappingContent])((prev, item) => prev + (item._1 -> rc(item._2)))
          )
        )
      }

    rc(mapping)
    // todo mszmal: add tests
  }

  def toMappingDto(mappingContent: MappingContent): MappingDto = {
    def rc(mappingContent: MappingContent): MappingDto =
      MappingDto(
        // 😍
        mappingContent.left.map(Left(_))
          .orElse(mappingContent.right.map(v => Right(v.iterator
            .foldLeft(Map.empty[String, MappingDto])((prev, item) => prev + (item._1 -> rc(item._2))))))
          .getOrElse(Right(Map.empty[String, MappingDto]))
      )

    rc(mappingContent)
  }
}