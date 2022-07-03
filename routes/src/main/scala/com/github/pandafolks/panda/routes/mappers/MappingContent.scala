package com.github.pandafolks.panda.routes.mappers

import com.github.pandafolks.panda.routes.dto.Mapping


final case class MappingContent(left: Option[String], right: Option[Map[String, MappingContent]]) // mongo cannot handle `Either`s

object MappingContent {
  def fromMapping(mapping: Mapping): MappingContent = {
    def rc(mapping: Mapping): MappingContent =
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

  def toMapping(mappingContent: MappingContent): Mapping = {
    def rc(mappingContent: MappingContent): Mapping =
      Mapping(
        // 😍
        mappingContent.left.map(Left(_))
          .orElse(mappingContent.right.map(v => Right(v.iterator
            .foldLeft(Map.empty[String, Mapping])((prev, item) => prev + (item._1 -> rc(item._2))))))
          .getOrElse(Right(Map.empty[String, Mapping]))
      )

    rc(mappingContent)
  }
}