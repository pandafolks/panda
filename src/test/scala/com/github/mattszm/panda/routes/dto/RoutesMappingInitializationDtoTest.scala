package com.github.mattszm.panda.routes.dto

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers.be
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

class RoutesMappingInitializationDtoTest extends AnyFlatSpec {
  "RoutesMappingInitializationDto#of" should "create appropriately mapped dto" in {
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
    val expected = RoutesMappingInitializationDto(
      Map.from(List(
        ("/cars", "cars"), ("cars/rent", "cars"), ("planes/{{plane_id}}/passengers", "planes")
      )),
      Map.from(List(
        ("cars", "/api/v1/"), ("planes", "/api/v2")
      ))
    )

    RoutesMappingInitializationDto.of(jsonToProcess) should be(expected)
  }
}
