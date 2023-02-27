package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.entity.{Mapper, MappingContent, Prefix}
import org.http4s.Method.{DELETE, GET, HEAD, POST, PUT}
import org.http4s.dsl.io.Path
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers.be
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

class RoutesTreesHandlerTest extends AnyFlatSpec {

  "getTree" should "return the tree and prefix based on the method" in {
    val handler = RoutesTreesHandler.construct(
      Map.from(List(
        (HttpMethod.Get(), List(Mapper("route", MappingContent(Option.empty, Option.empty), HttpMethod.Get(), true, 2L), Mapper("route2", MappingContent(Option.empty, Option.empty), HttpMethod.Get(), true, 3L))),
        (HttpMethod.Post(), List(Mapper("route3", MappingContent(Option.empty, Option.empty), HttpMethod.Post(), true, 2L), Mapper("route4", MappingContent(Option.empty, Option.empty), HttpMethod.Post(), true, 3L))),
        (HttpMethod.Delete(), List.empty[Mapper]),
      )),
      Map.from(List(("one", Prefix("one", "oneValue", 1L)), ("two", Prefix("two", "twoValue", 1L))))
    )

    val getTree: Option[RoutesTree] = handler.getTree(GET)
    getTree.isEmpty should be(false)
    getTree.get.find(Path.unsafeFromString("route")).isEmpty should be(false)
    getTree.get.find(Path.unsafeFromString("route2")).isEmpty should be(false)
    getTree.get.find(Path.unsafeFromString("route3")).isEmpty should be(true)

    val postTree: Option[RoutesTree] = handler.getTree(POST)
    postTree.isEmpty should be(false)
    postTree.get.find(Path.unsafeFromString("route")).isEmpty should be(true)
    postTree.get.find(Path.unsafeFromString("route2")).isEmpty should be(true)
    postTree.get.find(Path.unsafeFromString("route3")).isEmpty should be(false)
    postTree.get.find(Path.unsafeFromString("route4")).isEmpty should be(false)

    val putTree: Option[RoutesTree] = handler.getTree(PUT)
    putTree.isEmpty should be(false)

    val deleteTree: Option[RoutesTree] = handler.getTree(DELETE)
    deleteTree.isEmpty should be(false)

    val headTree: Option[RoutesTree] = handler.getTree(HEAD)
    headTree.isEmpty should be(true)

    handler.getPrefix("one").get should be("oneValue")
    handler.getPrefix("two").get should be("twoValue")
    handler.getPrefix("three").isEmpty should be(true)
  }
}
