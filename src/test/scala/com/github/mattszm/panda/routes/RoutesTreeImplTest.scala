package com.github.mattszm.panda.routes

import com.github.mattszm.panda.routes.dto.RoutesMappingInitializationDto
import org.http4s.Uri.Path
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers.{be, contain}
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

class RoutesTreeImplTest extends AnyFlatSpec {
  private val commonData = RoutesMappingInitializationDto(
    Map.from(List(
      ("/cars", "cars"), ("cars/rent", "cars"), ("planes/{{plane_id}}/passengers", "planes")
    )),
    Map.from(List(
      ("cars", "/api/v1/"), ("planes", "/api/v2")
    ))
  )

  "RoutesTree#construct" should "construct appropriate tree" in {
    val tree: RoutesTree = RoutesTreeImpl.construct(commonData)

    tree.getRoot.value should be(RoutesTree.Wildcard)

    val childrenFirstLayer = tree.getRoot.children
    childrenFirstLayer.map(_.value) should contain theSameElementsAs List(RoutesTree.Fixed("api"))
    childrenFirstLayer.filter(_.value == RoutesTree.Fixed("api")).head.groupInfo should be(None)

    val childrenSecondLayer = childrenFirstLayer.flatMap(_.children)
    childrenSecondLayer.map(_.value) should contain theSameElementsAs List(
      RoutesTree.Fixed("v1"), RoutesTree.Fixed("v2")
    )
    childrenSecondLayer.filter(_.value == RoutesTree.Fixed("v1")).head.groupInfo should be(None)
    childrenSecondLayer.filter(_.value == RoutesTree.Fixed("v2")).head.groupInfo should be(None)


    val childrenThirdLayer = childrenSecondLayer.flatMap(_.children)
    childrenThirdLayer.map(_.value) should contain theSameElementsAs List(
      RoutesTree.Fixed("cars"), RoutesTree.Fixed("planes"))
    childrenThirdLayer.filter(_.value == RoutesTree.Fixed("cars")).head.groupInfo should be(
      Some(GroupInfo(group = Group("cars"), prefix = "api/v1")))
    childrenThirdLayer.filter(_.value == RoutesTree.Fixed("planes")).head.groupInfo should be(None)

    val childrenFourthLayer = childrenThirdLayer.flatMap(_.children)
    childrenFourthLayer.map(_.value) should contain theSameElementsAs List(
      RoutesTree.Fixed("rent"), RoutesTree.Wildcard)
    childrenFourthLayer.filter(_.value == RoutesTree.Fixed("rent")).head.groupInfo should be(
      Some(GroupInfo(group = Group("cars"), prefix = "api/v1")))
    childrenFourthLayer.filter(_.value == RoutesTree.Wildcard).head.groupInfo should be(None)

    val childrenFifthLayer = childrenFourthLayer.flatMap(_.children)
    childrenFifthLayer.size should be(1)
    childrenFifthLayer.head.value should be(RoutesTree.Fixed("passengers"))
    childrenFifthLayer.head.children.size should be(0)
    childrenFifthLayer.head.groupInfo should be(
      Some(GroupInfo(group = Group("planes"), prefix = "api/v2")))

    val childrenSixthLayer = childrenFifthLayer.flatMap(_.children)
    childrenSixthLayer.size should be(0)
  }

  it should "handle scenario when there is no prefix in delivered data" in {
    val data = RoutesMappingInitializationDto(
      Map.from(List(
        ("/cars", "cars")
      )),
      Map.from(List(
        ("planes", "/api/v2")
      ))
    )

    val tree: RoutesTree = RoutesTreeImpl.construct(data)

    tree.getRoot.value should be(RoutesTree.Wildcard)
    val childrenFirstLayer = tree.getRoot.children
    childrenFirstLayer.size should be(1)
    childrenFirstLayer.head.value should be(RoutesTree.Fixed("cars"))
    childrenFirstLayer.head.children.size should be(0)
    childrenFirstLayer.head.groupInfo should be(Some(GroupInfo(group = Group("cars"), prefix = "")))
  }

  it should "put the Wildcard at the end of the children" in {
    val data = RoutesMappingInitializationDto(
      Map.from(List(
        ("/cars", "cars"),
        ("/bikes/", "cars"),
        ("/cars/blah", "cars"),
        ("{{some_wildcard}}", "cars"),
        ("planes/", "planes"),
        ("ships", "shipsGroup")
      )),
      Map.empty
    )

    val tree: RoutesTree = RoutesTreeImpl.construct(data)

    val children = tree.getRoot.children
    children.size should be(5)
    children.map(_.value) should contain theSameElementsAs List(
      RoutesTree.Fixed("cars"), RoutesTree.Fixed("bikes"), RoutesTree.Fixed("planes"),
      RoutesTree.Fixed("ships"), RoutesTree.Wildcard)
    children.last.value should be(RoutesTree.Wildcard)
  }

  "RoutesTree#construct#specifyGroup" should "return matching Group Info if exists" in {
    val tree: RoutesTree = RoutesTreeImpl.construct(commonData)

    tree.specifyGroup(Path.unsafeFromString("/api/v1/cars")) should be(
      Some(GroupInfo(group = Group("cars"), "api/v1")))
    tree.specifyGroup(Path.unsafeFromString("api/v1/cars/rent//")) should be(
      Some(GroupInfo(group = Group("cars"), "api/v1")))
    tree.specifyGroup(Path.unsafeFromString("api/v2/planes/somePlaneId123/passengers")) should be(
      Some(GroupInfo(group = Group("planes"), "api/v2")))
    tree.specifyGroup(Path.unsafeFromString("/api/v2/planes/random Id 213/passengers")) should be(
      Some(GroupInfo(group = Group("planes"), "api/v2")))
  }

  it should "return None if there is no matching group" in {
    val tree: RoutesTree = RoutesTreeImpl.construct(commonData)

    tree.specifyGroup(Path.unsafeFromString("/api/v1/cafrfds")) should be(None)
    tree.specifyGroup(Path.unsafeFromString("/api/v1/cars/random")) should be(None)
  }

  it should "return None if the provided path is empty" in {
    val tree: RoutesTree = RoutesTreeImpl.construct(commonData)

    tree.specifyGroup(Path.unsafeFromString("")) should be(None)
  }

  it should "always return None if there are no available routes" in {
    val tree: RoutesTree = RoutesTreeImpl.construct(RoutesMappingInitializationDto(Map.empty, Map.empty))

    tree.specifyGroup(Path.unsafeFromString("/api/v1/cars/random")) should be(None)
    tree.specifyGroup(Path.unsafeFromString("api/v2/planes/somePlaneId123/passengers/")) should be(None)
  }
}
