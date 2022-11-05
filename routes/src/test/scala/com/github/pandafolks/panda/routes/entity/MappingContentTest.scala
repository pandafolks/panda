package com.github.pandafolks.panda.routes.entity

import com.github.pandafolks.panda.routes.payload.MappingPayload
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers.be
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

class MappingContentTest extends AnyFlatSpec {
  private val mappingPayload = MappingPayload(
    Right(
      Map(
        "property1" -> MappingPayload(Left("someEndpoint1")),
        "property2" -> MappingPayload(Right(Map("property22" -> MappingPayload(Left("someEndpoint2"))))),
        "property3" -> MappingPayload(
          Right(
            Map(
              "property4" -> MappingPayload(Right(Map("property5" -> MappingPayload(Left("someEndpoint3"))))),
              "property6" -> MappingPayload(Left("someEndpoint4"))
            )
          )
        )
      )
    )
  )

  private val mappingContent = MappingContent(
    left = Option.empty,
    right = Some(
      Map(
        "property1" -> MappingContent(left = Some("someEndpoint1"), right = Option.empty),
        "property2" -> MappingContent(
          left = Option.empty,
          right = Some(
            Map(
              "property22" -> MappingContent(left = Some("someEndpoint2"), right = Option.empty)
            )
          )
        ),
        "property3" -> MappingContent(
          left = Option.empty,
          right = Some(
            Map(
              "property4" -> MappingContent(
                left = Option.empty,
                right = Some(
                  Map(
                    "property5" -> MappingContent(left = Some("someEndpoint3"), right = Option.empty)
                  )
                )
              ),
              "property6" -> MappingContent(left = Some("someEndpoint4"), right = Option.empty)
            )
          )
        )
      )
    )
  )

  "fromMappingPayload" should "create MappingContent based on MappingPayload" in {
    MappingContent.fromMappingPayload(mappingPayload) should be(mappingContent)
  }

  "toMappingPayload" should "create MappingPayload based on MappingContent" in {
    MappingContent.toMappingPayload(mappingContent) should be(mappingPayload)
  }

}
