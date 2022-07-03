package com.github.pandafolks.panda.routes.entity

import com.github.pandafolks.panda.routes.payload.MappingPayload

final case class MappingContent(left: Option[String], right: Option[Map[String, MappingContent]]) // mongo cannot handle `Either`s

object MappingContent {
  def fromMappingPayload(mapping: MappingPayload): MappingContent = {
    def rc(mapping: MappingPayload): MappingContent =
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

  def toMappingPayload(mappingContent: MappingContent): MappingPayload = {
    def rc(mappingContent: MappingContent): MappingPayload =
      MappingPayload(
        // 😍
        mappingContent.left.map(Left(_))
          .orElse(mappingContent.right.map(v => Right(v.iterator
            .foldLeft(Map.empty[String, MappingPayload])((prev, item) => prev + (item._1 -> rc(item._2))))))
          .getOrElse(Right(Map.empty[String, MappingPayload]))
      )

    rc(mappingContent)
  }
}