package com.github.mattszm.panda.routes.dto

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers.be
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

class RoutesMappingInitDtoTest extends AnyFlatSpec {
  "RoutesMappingInitDto#of" should "create appropriately mapped dto" in {
    val input: String = """{
                         |  "mappers": {
                         |    "/cars": "cars",
                         |    "cars/rent": "cars",
                         |    "planes/{{plane_id}}/passengers": "planes"
                         |  },
                         |  "prefixes": {
                         |    "cars": "/api/v1/",
                         |    "planes": "/api/v2"
                         |  }
                         |}""".stripMargin
    val jsonToProcess = ujson.read(input)
    val expected = RoutesMappingInitDto(
      Map.from(List(
        ("/cars", "cars"), ("cars/rent", "cars"), ("planes/{{plane_id}}/passengers", "planes")
      )),
      Map.from(List(
        ("cars", "/api/v1/"), ("planes", "/api/v2")
      ))
    )

    RoutesMappingInitDto.of(jsonToProcess) should be(expected)
  }
}
