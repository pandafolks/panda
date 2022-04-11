package com.mattszm.panda.routes

import com.mattszm.panda.routes.dto.RoutesMappingInitializationDto
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers.{be, contain}
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

class RoutesTreeTest extends AnyFlatSpec {
 "RoutesTree#construct" should "construct appropriate tree" in {
  // arrange
  val data = RoutesMappingInitializationDto(
   Map.from(List(
    ("/cars", "cars"), ("cars/rent", "cars"), ("planes/{{plane_id}}/passengers", "planes")
   )),
   Map.from(List(
    ("cars", "/api/v1/"), ("planes", "/api/v2")
   ))
  )

  // act
  val tree: RoutesTree = RoutesTree.construct(data)

  // assert
  tree.getHead.value should be(RoutesTree.Wildcard)

  val childrenFirstLayer = tree.getHead.children
  childrenFirstLayer.map(_.value) should contain theSameElementsAs List(
   RoutesTree.Fixed("cars"), RoutesTree.Fixed("planes"))
  childrenFirstLayer.filter(_.value == RoutesTree.Fixed("cars")).head.groupInfo should be(
   Some(RoutesTree.GroupInfo(group = Group("cars"), prefix = "api/v1")))
  childrenFirstLayer.filter(_.value == RoutesTree.Fixed("planes")).head.groupInfo should be(None)

  val childrenSecondLayer = childrenFirstLayer.flatMap(_.children)
  childrenSecondLayer.map(_.value) should contain theSameElementsAs List(
   RoutesTree.Fixed("rent"), RoutesTree.Wildcard)
  childrenSecondLayer.filter(_.value == RoutesTree.Fixed("rent")).head.groupInfo should be(
   Some(RoutesTree.GroupInfo(group = Group("cars"), prefix = "api/v1")))
  childrenSecondLayer.filter(_.value == RoutesTree.Wildcard).head.groupInfo should be(None)

  val childrenThirdLayer = childrenSecondLayer.flatMap(_.children)
  childrenThirdLayer.size should be(1)
  childrenThirdLayer.head.value should be(RoutesTree.Fixed("passengers"))
  childrenThirdLayer.head.children.size should be(0)
  childrenThirdLayer.head.groupInfo should be(
   Some(RoutesTree.GroupInfo(group = Group("planes"), prefix = "api/v2")))

  val childrenFourthLayer = childrenThirdLayer.flatMap(_.children)
  childrenFourthLayer.size should be(0)
 }

 it should "handle scenario when there is no prefix in delivered data" in {
  // arrange
  val data = RoutesMappingInitializationDto(
   Map.from(List(
    ("/cars", "cars")
   )),
   Map.from(List(
    ("planes", "/api/v2")
   ))
  )

  // act
  val tree: RoutesTree = RoutesTree.construct(data)

  // assert
  tree.getHead.value should be(RoutesTree.Wildcard)
  val childrenFirstLayer = tree.getHead.children
  childrenFirstLayer.size should be(1)
  childrenFirstLayer.head.value should be(RoutesTree.Fixed("cars"))
  childrenFirstLayer.head.children.size should be(0)
  childrenFirstLayer.head.groupInfo should be(Some(RoutesTree.GroupInfo(group = Group("cars"), prefix = "")))
 }
}
