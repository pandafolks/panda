package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.entity.{Mapper, MappingContent, Prefix}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers.be
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

class TreesServiceImplTest extends AnyFlatSpec {

  private def getMapper(timestamp: Long): Mapper = Mapper(
    route = "route",
    mappingContent = MappingContent(Option.empty, Option.empty),
    httpMethod = HttpMethod.Get(),
    isStandalone = false,
    lastUpdateTimestamp = timestamp
  )

  private def getPrefix(timestamp: Long): Prefix = Prefix(
    groupName = "groupName",
    value = "value",
    lastUpdateTimestamp = timestamp
  )

  "findLatestSeenMappingTimestamp" should "return the largest timestamp" in {
    val input: Map[HttpMethod, List[Mapper]] = Map.from(
      List(
        (HttpMethod.Get(), List(getMapper(1L), getMapper(123L))),
        (HttpMethod.Post(), List(getMapper(1L), getMapper(421L), getMapper(142L))),
        (HttpMethod.Delete(), List.empty)
      )
    )

    TreesServiceImpl.findLatestSeenMappingTimestamp(input) should be(421L)
  }

  it should "return 0L if there is no entries" in {
    TreesServiceImpl.findLatestSeenMappingTimestamp(Map.empty) should be(0L)
    TreesServiceImpl.findLatestSeenMappingTimestamp(
      Map.from(List((HttpMethod.Get(), List.empty), (HttpMethod.Patch(), List.empty)))
    ) should be(0L)
  }

  "findLatestSeenPrefixTimestamp" should "return the largest timestamp" in {
    val input: Map[String, Prefix] = Map.from(
      List(
        ("ONE", getPrefix(123L)),
        ("TWO", getPrefix(421L)),
        ("THREE", getPrefix(41L))
      )
    )

    TreesServiceImpl.findLatestSeenPrefixTimestamp(input) should be(421L)
  }

  it should "return 0L if there is no entries" in {
    TreesServiceImpl.findLatestSeenPrefixTimestamp(Map.empty) should be(0L)
  }
}
