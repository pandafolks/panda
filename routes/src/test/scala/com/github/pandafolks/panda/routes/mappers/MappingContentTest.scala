package com.github.pandafolks.panda.routes.mappers

import com.github.pandafolks.panda.routes.dto.MappingDto
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers.be
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

class MappingContentTest extends AnyFlatSpec {
  private val mappingDto = MappingDto(
    Right(Map(
      "property1" -> MappingDto(Left("someEndpoint1")),
      "property2" -> MappingDto(Right(Map("property22" -> MappingDto(Left("someEndpoint2"))))),
      "property3" -> MappingDto(Right(Map(
        "property4" -> MappingDto(Right(Map("property5" -> MappingDto(Left("someEndpoint3"))))),
        "property6" -> MappingDto(Left("someEndpoint4"))
      )))
    )
    ))

  private val mappingContent = MappingContent(
    left = Option.empty,
    right = Some(Map(
      "property1" -> MappingContent(left = Some("someEndpoint1"), right = Option.empty),
      "property2" -> MappingContent(
        left = Option.empty,
        right = Some(Map(
          "property22" -> MappingContent(left = Some("someEndpoint2"), right = Option.empty)
        ))
      ),
      "property3" -> MappingContent(
        left = Option.empty,
        right = Some(Map(
          "property4" -> MappingContent(
            left = Option.empty,
            right = Some(Map(
              "property5" -> MappingContent(left = Some("someEndpoint3"), right = Option.empty)
            ))
          ),
          "property6" -> MappingContent(left = Some("someEndpoint4"), right = Option.empty)
        ))
      )
    ))
  )

  "fromMappingDto" should "create MappingContent based on MappingDto" in {
    MappingContent.fromMappingDto(mappingDto) should be(mappingContent)
  }

  "toMappingDto" should "create MappingDto based on MappingContent" in {
    MappingContent.toMappingDto(mappingContent) should be(mappingDto)
  }

}
