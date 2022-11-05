package com.github.pandafolks.panda.routes.entity

import com.github.pandafolks.panda.routes.payload.MappingPayload
import com.github.pandafolks.panda.utils.EscapeUtils.unifyFromSlashes

final case class MappingContent(
    left: Option[String],
    right: Option[Map[String, MappingContent]]
) // Mongo cannot handle `Either`s

object MappingContent {
  def fromMappingPayload(mapping: MappingPayload): MappingContent = {
    def rc(mapping: MappingPayload, initial: Boolean): MappingContent =
      mapping.value match {
        case Left(v) =>
          MappingContent(
            left = Some(if (initial) v else unifyFromSlashes(v)),
            right = Option.empty
          ) // Group may contain leading or ending slashes. However, all routes have to be escaped.
        case Right(map) =>
          MappingContent(
            left = Option.empty,
            right = Some(
              map.iterator.foldLeft(Map.empty[String, MappingContent])((prev, item) =>
                prev + (item._1 -> rc(item._2, initial = false))
              )
            )
          )
      }

    rc(mapping, initial = true)
  }

  def toMappingPayload(mappingContent: MappingContent): MappingPayload = {
    def rc(mappingContent: MappingContent): MappingPayload =
      MappingPayload(
        mappingContent.left
          .map(Left(_))
          .orElse(
            mappingContent.right.map(v =>
              Right(
                v.iterator
                  .foldLeft(Map.empty[String, MappingPayload])((prev, item) => prev + (item._1 -> rc(item._2)))
              )
            )
          )
          .getOrElse(Right(Map.empty[String, MappingPayload]))
      )

    rc(mappingContent)
  }
}
