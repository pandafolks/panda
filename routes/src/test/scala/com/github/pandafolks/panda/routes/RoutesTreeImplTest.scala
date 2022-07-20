package com.github.pandafolks.panda.routes

//import org.http4s.Uri.Path
import org.scalatest.flatspec.AnyFlatSpec
//import org.scalatest.matchers.must.Matchers.{be, contain}
//import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

class RoutesTreeImplTest extends AnyFlatSpec {
//  private val commonData = RoutesMappingInitDto(
//    Map.from(List(
//      ("cars", "/api/v1/"), ("planes", "/api/v2")
//    )),
//    Map.from(List(
//      ("/cars", "cars"), ("cars/rent", "cars"), ("planes/{{plane_id}}/passengers", "planes"), ("/cars/pocket/**", "cars")
//    ))
//  )
//
//  "RoutesTree#construct" should "construct appropriate tree" in {
//    val tree: RoutesTree = RoutesTreeImpl.unifyPrefixesAndConstruct(commonData)
//
//    tree.getRoot.value should be(RoutesTree.Wildcard())
//
//    val childrenFirstLayer = tree.getRoot.children
//
//    childrenFirstLayer.map(_.value) should contain theSameElementsAs List(
//      RoutesTree.Fixed("cars"), RoutesTree.Fixed("planes"))
//    childrenFirstLayer.filter(_.value == RoutesTree.Fixed("cars")).head.routeInfo should be(
//      Some(RouteInfo(group = Group("cars"), prefix = Path.unsafeFromString("api/v1"))))
//    childrenFirstLayer.filter(_.value == RoutesTree.Fixed("planes")).head.routeInfo should be(None)
//
//    val childrenSecondLayer = childrenFirstLayer.flatMap(_.children)
//    childrenSecondLayer.map(_.value) should contain theSameElementsAs List(
//      RoutesTree.Fixed("pocket"), RoutesTree.Fixed("rent"), RoutesTree.Wildcard("plane_id")
//    )
//    childrenSecondLayer.map(_.value).last should be (RoutesTree.Wildcard("plane_id")) // wildcard is always last
//    childrenSecondLayer.filter(_.value == RoutesTree.Fixed("rent")).head.routeInfo should be(
//      Some(RouteInfo(group = Group("cars"), prefix = Path.unsafeFromString("api/v1"))))
//    childrenSecondLayer.filter(_.value == RoutesTree.Fixed("pocket")).head.routeInfo should be(
//      Some(RouteInfo(group = Group("cars"), prefix = Path.unsafeFromString("api/v1"), isPocket = true)))
//    childrenSecondLayer.filter(_.value == RoutesTree.Wildcard("plane_id")).head.routeInfo should be(None)
//
//    val childrenThirdLayer = childrenSecondLayer.flatMap(_.children)
//    childrenThirdLayer.size should be(1)
//    childrenThirdLayer.head.value should be(RoutesTree.Fixed("passengers"))
//    childrenThirdLayer.head.children.size should be(0)
//    childrenThirdLayer.head.routeInfo should be(
//      Some(RouteInfo(group = Group("planes"), prefix = Path.unsafeFromString("api/v2"))))
//
//    val childrenFourthLayer = childrenThirdLayer.flatMap(_.children)
//    childrenFourthLayer.size should be(0)
//  }
//
//  it should "handle scenario when there is no prefix in delivered data" in {
//    val data = RoutesMappingInitDto(
//      Map.from(List(
//        ("planes", "/api/v2")
//      )),
//      Map.from(List(
//        ("/cars", "cars")
//      ))
//    )
//
//    val tree: RoutesTree = RoutesTreeImpl.unifyPrefixesAndConstruct(data)
//
//    tree.getRoot.value should be(RoutesTree.Wildcard())
//    val childrenFirstLayer = tree.getRoot.children
//    childrenFirstLayer.size should be(1)
//    childrenFirstLayer.head.value should be(RoutesTree.Fixed("cars"))
//    childrenFirstLayer.head.children.size should be(0)
//    childrenFirstLayer.head.routeInfo should be(Some(RouteInfo(group = Group("cars"),
//      prefix = Path.unsafeFromString(""))))
//  }
//
//  it should "put the Wildcard at the end of the children" in {
//    val data = RoutesMappingInitDto(
//      Map.empty,
//      Map.from(List(
//        ("/cars", "cars"),
//        ("/bikes/", "cars"),
//        ("/cars/blah", "cars"),
//        ("{{some_wildcard}}", "cars"),
//        ("planes/", "planes"),
//        ("ships", "shipsGroup")
//      ))
//    )
//
//    val tree: RoutesTree = RoutesTreeImpl.unifyPrefixesAndConstruct(data)
//
//    val children = tree.getRoot.children
//    children.size should be(5)
//    children.map(_.value) should contain theSameElementsAs List(
//      RoutesTree.Fixed("cars"), RoutesTree.Fixed("bikes"), RoutesTree.Fixed("planes"),
//      RoutesTree.Fixed("ships"), RoutesTree.Wildcard("some_wildcard"))
//    children.last.value should be(RoutesTree.Wildcard("some_wildcard"))
//  }
//
//  "RoutesTree#construct#specifyGroup" should "return matching Group Info if exists" in {
//    val tree: RoutesTree = RoutesTreeImpl.unifyPrefixesAndConstruct(commonData)
//
//    tree.specifyGroup(Path.unsafeFromString("cars")) should be(
//      Some((RouteInfo(group = Group("cars"), Path.unsafeFromString("api/v1")), Map.empty[String, String])))
//
//    tree.specifyGroup(Path.unsafeFromString("cars/rent//")) should be(
//      Some((RouteInfo(group = Group("cars"), Path.unsafeFromString("api/v1")), Map.empty[String, String])))
//
//    tree.specifyGroup(Path.unsafeFromString("planes/somePlaneId123/passengers/")) should be(
//      Some((RouteInfo(group = Group("planes"), Path.unsafeFromString("api/v2")), Map("plane_id" -> "somePlaneId123"))))
//
//    tree.specifyGroup(Path.unsafeFromString("/planes/random Id 213/passengers")) should be(
//      Some((RouteInfo(group = Group("planes"), Path.unsafeFromString("api/v2")), Map("plane_id" -> "random Id 213"))))
//
//    tree.specifyGroup(Path.unsafeFromString("cars/pocket/whatever")) should be(
//      Some((RouteInfo(group = Group("cars"), Path.unsafeFromString("api/v1"), isPocket = true), Map.empty[String, String])))
//
//    tree.specifyGroup(Path.unsafeFromString("cars/pocket/whatever/whatever2/whatever3/")) should be(
//      Some((RouteInfo(group = Group("cars"), Path.unsafeFromString("api/v1"), isPocket = true), Map.empty[String, String])))
//  }
//
//  it should "return None if there is no matching group" in {
//    val tree: RoutesTree = RoutesTreeImpl.unifyPrefixesAndConstruct(commonData)
//
//    tree.specifyGroup(Path.unsafeFromString("/cafrfds")) should be(None)
//    tree.specifyGroup(Path.unsafeFromString("/cars/random")) should be(None)
//  }
//
//  it should "return None if the provided path is empty" in {
//    val tree: RoutesTree = RoutesTreeImpl.unifyPrefixesAndConstruct(commonData)
//
//    tree.specifyGroup(Path.unsafeFromString("")) should be(None)
//  }
//
//  it should "always return None if there are no available routes" in {
//    val tree: RoutesTree = RoutesTreeImpl.unifyPrefixesAndConstruct(RoutesMappingInitDto(Map.empty, Map.empty))
//
//    tree.specifyGroup(Path.unsafeFromString("/cars/random")) should be(None)
//    tree.specifyGroup(Path.unsafeFromString("planes/somePlaneId123/passengers/")) should be(None)
//  }
//
//  it should "should handle Pocket while favoring Fixed paths and Wildcards" in {
//    val tree: RoutesTree = RoutesTreeImpl.unifyPrefixesAndConstruct(RoutesMappingInitDto(
//      Map.empty,
//      Map.from(List(
//        ("supercars/fixed/", "fixedGroup"),
//        ("supercars/**", "pocketGroup"),
//        ("supercars/blabla/{{blabla_id}}", "wildcardGroup"),
//        ("supercars/fixed/fixed2", "fixedGroup2"),
//      ))
//    ))
//
//    tree.specifyGroup(Path.unsafeFromString("supercars/fixed")) should be(
//      Some((RouteInfo(group = Group("fixedGroup"), Path.empty), Map.empty[String, String])))
//    tree.specifyGroup(Path.unsafeFromString("supercars/fixed/fixed2")) should be(
//      Some((RouteInfo(group = Group("fixedGroup2"), Path.empty), Map.empty[String, String])))
//
//    tree.specifyGroup(Path.unsafeFromString("supercars/blabla/someId")) should be(
//      Some((RouteInfo(group = Group("wildcardGroup"), Path.empty), Map("blabla_id" -> "someId"))))
//
//    tree.specifyGroup(Path.unsafeFromString("supercars/whatever/whatever2")) should be(
//      Some((RouteInfo(group = Group("pocketGroup"), Path.empty, isPocket = true), Map.empty[String, String])))
//
//    // Corner case - even if there is `supercars/blabla/{{blabla if}}`, the `routeInfo` will be still a Pocket.
//    // This is because there is no direct match, so the most appropriate Pocket is chosen.
//    tree.specifyGroup(Path.unsafeFromString("supercars/blabla/whatever/whatever2/")) should be(
//      Some((RouteInfo(group = Group("pocketGroup"), Path.empty, isPocket = true), Map.empty[String, String])))
//  }
//
//  it should "return be able to return multiple Wildcard mappings" in {
//    val tree: RoutesTree = RoutesTreeImpl.unifyPrefixesAndConstruct(RoutesMappingInitDto(
//      Map.empty,
//      Map.from(List(
//        ("supercars/blabla/{{first_wildcard}}", "wildcardGroup1"),
//        ("supercars/blabla/{{first_wildcard}}/someFixed", "wildcardGroup2"),
//        ("supercars/blabla/{{first_wildcard}}/{{second_wildcard}}/blabla2/{{third_wildcard}}", "wildcardGroup3"),
//      ))
//    ))
//
//    tree.specifyGroup(Path.unsafeFromString("supercars/blabla/someId")) should be(
//      Some((RouteInfo(group = Group("wildcardGroup1"), Path.empty), Map("first_wildcard" -> "someId"))))
//
//    tree.specifyGroup(Path.unsafeFromString("supercars/blabla/someId2/someFixed")) should be(
//      Some((RouteInfo(group = Group("wildcardGroup2"), Path.empty), Map("first_wildcard" -> "someId2"))))
//
//    tree.specifyGroup(Path.unsafeFromString("supercars/blabla/firstWildcardValue/secondWildcardValue/blabla2/thirdWildcardValue")) should be(
//      Some((RouteInfo(group = Group("wildcardGroup3"), Path.empty),
//        Map("first_wildcard" -> "firstWildcardValue", "second_wildcard" -> "secondWildcardValue", "third_wildcard" -> "thirdWildcardValue")
//      )))
//  }

}
