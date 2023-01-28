package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.RoutesTree.RouteInfo
import com.github.pandafolks.panda.routes.entity.{Mapper, MappingContent}
import org.http4s.dsl.io.Path
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers.{be, contain}
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

class RoutesTreeImplTest extends AnyFlatSpec {
  // simple mapping MappingContent, the complex ones don't bring any value in these tests.
  private val commonData = List(
    Mapper("/cars", MappingContent(Some("cars"), Option.empty), HttpMethod.Get(), isStandalone = true, -1),
    Mapper("/cars/rent", MappingContent(Some("cars"), Option.empty), HttpMethod.Get(), isStandalone = false, -1),
    Mapper(
      "planes/{{plane_id}}/passengers",
      MappingContent(Some("planes"), Option.empty),
      HttpMethod.Get(),
      isStandalone = true,
      -1
    ),
    Mapper("/cars/pocket/**", MappingContent(Some("cars"), Option.empty), HttpMethod.Get(), isStandalone = true, -1)
  )

  "RoutesTree#construct" should "construct appropriate tree" in {
    val tree: RoutesTree = RoutesTreeImpl.construct(commonData)

    tree.getRoot.value should be(RoutesTree.Wildcard())

    val childrenFirstLayer = tree.getRoot.children

    childrenFirstLayer.map(_.value) should contain theSameElementsAs List(
      RoutesTree.Fixed("cars"),
      RoutesTree.Fixed("planes")
    )
    childrenFirstLayer.filter(_.value == RoutesTree.Fixed("cars")).head.routeInfo should be(
      Some(RouteInfo(mappingContent = MappingContent(Some("cars"), Option.empty)))
    )
    childrenFirstLayer.filter(_.value == RoutesTree.Fixed("planes")).head.routeInfo should be(None)

    val childrenSecondLayer = childrenFirstLayer.flatMap(_.children)
    childrenSecondLayer.map(_.value) should contain theSameElementsAs List(
      RoutesTree.Fixed("pocket"),
      RoutesTree.Fixed("rent"),
      RoutesTree.Wildcard("plane_id")
    )
    childrenSecondLayer.map(_.value).last should be(RoutesTree.Wildcard("plane_id")) // wildcard is always last
    childrenSecondLayer.filter(_.value == RoutesTree.Fixed("rent")).head.routeInfo should be(
      Some(RouteInfo(mappingContent = MappingContent(Some("cars"), Option.empty), isStandalone = false))
    )
    childrenSecondLayer.filter(_.value == RoutesTree.Fixed("pocket")).head.routeInfo should be(
      Some(RouteInfo(mappingContent = MappingContent(Some("cars"), Option.empty), isPocket = true))
    )

    childrenSecondLayer.filter(_.value == RoutesTree.Wildcard("plane_id")).head.routeInfo should be(None)

    val childrenThirdLayer = childrenSecondLayer.flatMap(_.children)
    childrenThirdLayer.size should be(1)
    childrenThirdLayer.head.value should be(RoutesTree.Fixed("passengers"))
    childrenThirdLayer.head.children.size should be(0)

    childrenThirdLayer.head.routeInfo should be(
      Some(RouteInfo(mappingContent = MappingContent(Some("planes"), Option.empty)))
    )

    val childrenFourthLayer = childrenThirdLayer.flatMap(_.children)
    childrenFourthLayer.size should be(0)
  }

  it should "put the Wildcard at the end of the children" in {
    val data = List(
      Mapper("/cars", MappingContent(Some("cars"), Option.empty), HttpMethod.Get(), isStandalone = true, -1),
      Mapper("/bikes/", MappingContent(Some("cars"), Option.empty), HttpMethod.Get(), isStandalone = true, -1),
      Mapper("/cars/blah", MappingContent(Some("cars"), Option.empty), HttpMethod.Get(), isStandalone = true, -1),
      Mapper(
        "{{some_wildcard}}",
        MappingContent(Some("cars"), Option.empty),
        HttpMethod.Get(),
        isStandalone = true,
        -1
      ),
      Mapper("planes/", MappingContent(Some("planes"), Option.empty), HttpMethod.Get(), isStandalone = true, -1),
      Mapper("ships", MappingContent(Some("shipsGroup"), Option.empty), HttpMethod.Get(), isStandalone = true, -1)
    )

    val tree: RoutesTree = RoutesTreeImpl.construct(data)

    val children = tree.getRoot.children
    children.size should be(5)
    children.map(_.value) should contain theSameElementsAs List(
      RoutesTree.Fixed("cars"),
      RoutesTree.Fixed("bikes"),
      RoutesTree.Fixed("planes"),
      RoutesTree.Fixed("ships"),
      RoutesTree.Wildcard("some_wildcard")
    )
    children.last.value should be(RoutesTree.Wildcard("some_wildcard"))
  }

  "RoutesTree#construct#find" should "return matching Group Info if exists" in {
    val tree: RoutesTree = RoutesTreeImpl.construct(commonData)

    tree.find(Path.unsafeFromString("cars")) should be(
      Some((RouteInfo(mappingContent = MappingContent(Some("cars"), Option.empty)), Map.empty[String, String]))
    )

    tree.find(Path.unsafeFromString("cars/rent//")) should be(
      Some(
        (
          RouteInfo(mappingContent = MappingContent(Some("cars"), Option.empty), isStandalone = false),
          Map.empty[String, String]
        )
      )
    )

    tree.find(Path.unsafeFromString("planes/somePlaneId123/passengers/")) should be(
      Some(
        (RouteInfo(mappingContent = MappingContent(Some("planes"), Option.empty)), Map("plane_id" -> "somePlaneId123"))
      )
    )

    tree.find(Path.unsafeFromString("/planes/random Id 213/passengers")) should be(
      Some(
        (RouteInfo(mappingContent = MappingContent(Some("planes"), Option.empty)), Map("plane_id" -> "random Id 213"))
      )
    )

    tree.find(Path.unsafeFromString("cars/pocket/whatever")) should be(
      Some(
        (
          RouteInfo(mappingContent = MappingContent(Some("cars"), Option.empty), isPocket = true),
          Map.empty[String, String]
        )
      )
    )

    tree.find(Path.unsafeFromString("cars/pocket/whatever/whatever2/whatever3/")) should be(
      Some(
        (
          RouteInfo(mappingContent = MappingContent(Some("cars"), Option.empty), isPocket = true),
          Map.empty[String, String]
        )
      )
    )
  }

  it should "return None if there is no matching group" in {
    val tree: RoutesTree = RoutesTreeImpl.construct(commonData)

    tree.find(Path.unsafeFromString("/cafrfds")) should be(None)
    tree.find(Path.unsafeFromString("/cars/random")) should be(None)
  }

  it should "return None if there is a match but the result is not standalone and standaloneOnly is requested" in {
    val tree: RoutesTree = RoutesTreeImpl.construct(commonData)

    tree.find(Path.unsafeFromString("/cars/rent/"), standaloneOnly = true) should be(None)
  }

  it should "return None if the provided path is empty" in {
    val tree: RoutesTree = RoutesTreeImpl.construct(commonData)

    tree.find(Path.unsafeFromString("")) should be(None)
  }

  it should "always return None if there are no available routes" in {
    val tree: RoutesTree = RoutesTreeImpl.construct(List.empty)

    tree.find(Path.unsafeFromString("/cars/random")) should be(None)
    tree.find(Path.unsafeFromString("planes/somePlaneId123/passengers/")) should be(None)
  }

  it should "should handle Pocket while favoring Fixed paths and Wildcards" in {
    val tree: RoutesTree = RoutesTreeImpl.construct(
      List(
        Mapper(
          "supercars/fixed/",
          MappingContent(Some("fixedGroup"), Option.empty),
          HttpMethod.Get(),
          isStandalone = true,
          -1
        ),
        Mapper(
          "supercars/**",
          MappingContent(Some("pocketGroup"), Option.empty),
          HttpMethod.Get(),
          isStandalone = true,
          -1
        ),
        Mapper(
          "supercars/blabla/{{blabla_id}}",
          MappingContent(Some("wildcardGroup"), Option.empty),
          HttpMethod.Get(),
          isStandalone = true,
          -1
        ),
        Mapper(
          "supercars/fixed/fixed2",
          MappingContent(Some("fixedGroup2"), Option.empty),
          HttpMethod.Get(),
          isStandalone = true,
          -1
        )
      )
    )

    tree.find(Path.unsafeFromString("supercars/fixed")) should be(
      Some((RouteInfo(mappingContent = MappingContent(Some("fixedGroup"), Option.empty)), Map.empty[String, String]))
    )
    tree.find(Path.unsafeFromString("supercars/fixed/fixed2")) should be(
      Some((RouteInfo(mappingContent = MappingContent(Some("fixedGroup2"), Option.empty)), Map.empty[String, String]))
    )
    tree.find(Path.unsafeFromString("supercars/blabla/someId")) should be(
      Some(
        (RouteInfo(mappingContent = MappingContent(Some("wildcardGroup"), Option.empty)), Map("blabla_id" -> "someId"))
      )
    )
    tree.find(Path.unsafeFromString("supercars/whatever/whatever2")) should be(
      Some(
        (
          RouteInfo(mappingContent = MappingContent(Some("pocketGroup"), Option.empty), isPocket = true),
          Map.empty[String, String]
        )
      )
    )

    // Corner case - even if there is `supercars/blabla/{{blabla if}}`, the `routeInfo` will be still a Pocket.
    // This is because there is no direct match, so the most appropriate Pocket is chosen.
    tree.find(Path.unsafeFromString("supercars/blabla/whatever/whatever2/")) should be(
      Some(
        (
          RouteInfo(mappingContent = MappingContent(Some("pocketGroup"), Option.empty), isPocket = true),
          Map.empty[String, String]
        )
      )
    )
  }

  it should "return be able to return multiple Wildcard mappings" in {
    val tree: RoutesTree = RoutesTreeImpl.construct(
      List(
        Mapper(
          "supercars/blabla/{{first_wildcard}}",
          MappingContent(Some("wildcardGroup1"), Option.empty),
          HttpMethod.Get(),
          isStandalone = true,
          -1
        ),
        Mapper(
          "supercars/blabla/{{first_wildcard}}/someFixed",
          MappingContent(Some("wildcardGroup2"), Option.empty),
          HttpMethod.Get(),
          isStandalone = true,
          -1
        ),
        Mapper(
          "supercars/blabla/{{first_wildcard}}/{{second_wildcard}}/blabla2/{{third_wildcard}}",
          MappingContent(Some("wildcardGroup3"), Option.empty),
          HttpMethod.Get(),
          isStandalone = true,
          -1
        ),
        Mapper(
          "supercars/fixed/fixed2",
          MappingContent(Some("fixedGroup2"), Option.empty),
          HttpMethod.Get(),
          isStandalone = true,
          -1
        ),
        Mapper(
          "supercars2/blabla2/{{first_wildcard}}/someFixed2/**",
          MappingContent(Some("wildcardGroup4"), Option.empty),
          HttpMethod.Get(),
          isStandalone = false,
          -1
        )
      )
    )

    tree.find(Path.unsafeFromString("supercars/blabla/someId")) should be(
      Some(
        (
          RouteInfo(mappingContent = MappingContent(Some("wildcardGroup1"), Option.empty)),
          Map("first_wildcard" -> "someId")
        )
      )
    )

    tree.find(Path.unsafeFromString("supercars/blabla/someId2/someFixed")) should be(
      Some(
        (
          RouteInfo(mappingContent = MappingContent(Some("wildcardGroup2"), Option.empty)),
          Map("first_wildcard" -> "someId2")
        )
      )
    )

    tree.find(
      Path.unsafeFromString("supercars/blabla/firstWildcardValue/secondWildcardValue/blabla2/thirdWildcardValue")
    ) should be(
      Some(
        (
          RouteInfo(mappingContent = MappingContent(Some("wildcardGroup3"), Option.empty)),
          Map(
            "first_wildcard" -> "firstWildcardValue",
            "second_wildcard" -> "secondWildcardValue",
            "third_wildcard" -> "thirdWildcardValue"
          )
        )
      )
    )

    tree.find(
      Path.unsafeFromString("supercars2/blabla2/dadadadada/someFixed2/something1/something2")
    ) should be(
      Some(
        (
          RouteInfo(mappingContent = MappingContent(Some("wildcardGroup4"), Option.empty), isPocket = true, isStandalone = false),
          Map(
            "first_wildcard" -> "dadadadada"
          )
        )
      )
    )
  }

}
