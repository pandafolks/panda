package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.filter.StandaloneFilter
import com.github.pandafolks.panda.routes.payload.{
  MapperRecordPayload,
  MapperRemovePayload,
  MappingPayload,
  RoutesRemovePayload,
  RoutesResourcePayload
}
import com.github.pandafolks.panda.utils.NotExists
import com.github.pandafolks.panda.utils.scheduler.CoreScheduler
import monix.eval.Task
import monix.execution.Scheduler
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, EitherValues}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class RoutesServiceImplItTest
    extends AsyncFlatSpec
    with RoutesFixture
    with Matchers
    with ScalaFutures
    with EitherValues
    with BeforeAndAfterAll
    with BeforeAndAfterEach {
  implicit val scheduler: Scheduler = CoreScheduler.scheduler

  implicit val defaultConfig: PatienceConfig = PatienceConfig(30.seconds, 100.milliseconds)

  override protected def afterAll(): Unit = mongoContainer.stop()

  override protected def beforeEach(): Unit = Await.result(
    mappersAndPrefixesConnection.use { case (mappers, prefixes) =>
      mappers.db.dropCollection(dbName, mappersColName) >> prefixes.db.dropCollection(dbName, prefixesColName)
    }.runToFuture,
    5.seconds
  )

  private val payload1 = RoutesResourcePayload(
    mappers = Some(
      List(
        (
          "/route/one",
          MapperRecordPayload(
            MappingPayload(
              Right(
                Map(
                  "property1" -> MappingPayload(Left("someEndpoint1/")), // someEndpoint1 route should be escaped
                  "property2" -> MappingPayload(Right(Map("property22" -> MappingPayload(Left("someEndpoint2"))))),
                  "property3" -> MappingPayload(
                    Right(
                      Map(
                        "property4" -> MappingPayload(
                          Right(Map("property5" -> MappingPayload(Left("/someEndpoint3/"))))
                        ),
                        "property6" -> MappingPayload(Left("someEndpoint4"))
                      )
                    )
                  )
                )
              )
            ),
            Some("post"),
            Some(true)
          )
        ),
        ("/another/route/{{some_id}}", MapperRecordPayload(MappingPayload(Left("group1")))),
        (
          "groupRoute/**",
          MapperRecordPayload(
            MappingPayload(Left("group2/")),
            isStandalone = Some(false)
          ) // group name should not be escaped
        )
      )
    ),
    prefixes = Some(
      Map.from(
        List(
          ("group1 ", "/api/v1"),
          ("group2", "api/v2/")
        )
      )
    )
  )

  private val payload2 = RoutesResourcePayload(
    mappers = Some(
      List(
        (
          "/route/two",
          MapperRecordPayload(
            MappingPayload(
              Right(
                Map(
                  "property1" -> MappingPayload(Left("someEndpoint1"))
                )
              )
            ),
            Some("delete")
          )
        ),
        (
          "/groupRoute/**",
          MapperRecordPayload(
            MappingPayload(Left("group22")),
            isStandalone = Some(true)
          ) // duplicate (same route and same http method)
        ),
        (
          "/another/route/{{some_id}}/",
          MapperRecordPayload(MappingPayload(Left("group11"))) // duplicate (same route and same http method)
        ),
        (
          "/another/route/{{some_id}}",
          MapperRecordPayload(
            MappingPayload(Left("group1")),
            Some("put"),
            Some(false)
          ) // same route but another http method, so there is no duplicate
        )
      )
    ),
    prefixes = Some(
      Map.from(
        List(
          ("group1", "api/v1111"), // duplicate
          ("group3", "/api/v3/")
        )
      )
    )
  )

  private val expectedWithoutOverrides = RoutesResourcePayload(
    mappers = Some(
      List(
        (
          "route/one",
          MapperRecordPayload(
            MappingPayload(
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
            ),
            Some("POST"),
            Some(true)
          )
        ),
        ("another/route/{{some_id}}", MapperRecordPayload(MappingPayload(Left("group1")), Some("GET"), Some(true))),
        ("groupRoute/**", MapperRecordPayload(MappingPayload(Left("group2/")), Some("GET"), Some(false))),
        (
          "route/two",
          MapperRecordPayload(
            MappingPayload(
              Right(
                Map(
                  "property1" -> MappingPayload(Left("someEndpoint1"))
                )
              )
            ),
            Some("DELETE"),
            Some(true)
          )
        ),
        ("another/route/{{some_id}}", MapperRecordPayload(MappingPayload(Left("group1")), Some("PUT"), Some(false)))
      )
    ),
    prefixes = Some(
      Map.from(
        List(
          ("group1", "api/v1"),
          ("group2", "api/v2"),
          ("group3", "api/v3")
        )
      )
    )
  )

  private val expectedWithoutOverridesForStandaloneOnly = RoutesResourcePayload(
    mappers = Some(
      List(
        (
          "route/one",
          MapperRecordPayload(
            MappingPayload(
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
            ),
            Some("POST"),
            Some(true)
          )
        ),
        (
          "route/two",
          MapperRecordPayload(
            MappingPayload(
              Right(
                Map(
                  "property1" -> MappingPayload(Left("someEndpoint1"))
                )
              )
            ),
            Some("DELETE"),
            Some(true)
          )
        ),
        ("another/route/{{some_id}}", MapperRecordPayload(MappingPayload(Left("group1")), Some("GET"), Some(true)))
      )
    ),
    prefixes = Some(
      Map.from(
        List(
          ("group1", "api/v1"),
          ("group2", "api/v2"),
          ("group3", "api/v3")
        )
      )
    )
  )

  private val expectedWithoutOverridesForNonStandaloneOnly = RoutesResourcePayload(
    mappers = Some(
      List(
        ("groupRoute/**", MapperRecordPayload(MappingPayload(Left("group2/")), Some("GET"), Some(false))),
        ("another/route/{{some_id}}", MapperRecordPayload(MappingPayload(Left("group1")), Some("PUT"), Some(false)))
      )
    ),
    prefixes = Some(
      Map.from(
        List(
          ("group1", "api/v1"),
          ("group2", "api/v2"),
          ("group3", "api/v3")
        )
      )
    )
  )

  private val expectedWithOverrides = RoutesResourcePayload(
    mappers = Some(
      List(
        (
          "route/one",
          MapperRecordPayload(
            MappingPayload(
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
            ),
            Some("POST"),
            Some(true)
          )
        ),
        (
          "another/route/{{some_id}}",
          MapperRecordPayload(MappingPayload(Left("group11")), Some("GET"), Some(true)) // override
        ),
        (
          "groupRoute/**",
          MapperRecordPayload(MappingPayload(Left("group22")), Some("GET"), Some(true)) // override
        ),
        (
          "route/two",
          MapperRecordPayload(
            MappingPayload(
              Right(
                Map(
                  "property1" -> MappingPayload(Left("someEndpoint1"))
                )
              )
            ),
            Some("DELETE"),
            Some(true)
          )
        ),
        ("another/route/{{some_id}}", MapperRecordPayload(MappingPayload(Left("group1")), Some("PUT"), Some(false)))
      )
    ),
    prefixes = Some(
      Map.from(
        List(
          ("group1", "api/v1111"),
          ("group2", "api/v2"),
          ("group3", "api/v3")
        )
      )
    )
  )

  private val findByGroupPayload = RoutesResourcePayload(
    mappers = Some(
      List(
        (
          "route/one",
          MapperRecordPayload(
            MappingPayload(
              Right(
                Map(
                  "property1" -> MappingPayload(Left("someEndpoint1")),
                  "property2" -> MappingPayload(Right(Map("property22" -> MappingPayload(Left("someEndpoint2"))))),
                  "property3" -> MappingPayload(
                    Right(
                      Map(
                        "property4" -> MappingPayload(Right(Map("property5" -> MappingPayload(Left("someEndpoint3"))))),
                        "property6" -> MappingPayload(Left("someEndpoint4")),
                        "property7" -> MappingPayload(Left("someEndpoint1"))
                      )
                    )
                  )
                )
              )
            ),
            Some("POST"),
            Some(true)
          )
        ),
        ("another/route/{{some_id}}", MapperRecordPayload(MappingPayload(Left("group1")), Some("GET"), Some(true))),
        (
          "another/route/{{some_id}}/complex",
          MapperRecordPayload(
            MappingPayload(
              Right(
                Map(
                  "property3" -> MappingPayload(
                    Right(
                      Map(
                        "property4" -> MappingPayload(Right(Map("property5" -> MappingPayload(Left("someEndpoint3")))))
                      )
                    )
                  )
                )
              )
            ),
            Some("POST"),
            Some(true)
          )
        ),
        (
          "another/route/{{some_id}}/complex",
          MapperRecordPayload(
            MappingPayload(
              Right(
                Map(
                  "property3" -> MappingPayload(
                    Right(
                      Map(
                        "property4" -> MappingPayload(Right(Map("property5" -> MappingPayload(Left("someEndpoint3")))))
                      )
                    )
                  )
                )
              )
            ),
            Some("PATCH"),
            Some(true)
          )
        ),
        ("groupRoute/**", MapperRecordPayload(MappingPayload(Left("group2/")), Some("GET"), Some(false))),
        (
          "route/two",
          MapperRecordPayload(
            MappingPayload(
              Right(
                Map(
                  "property1" -> MappingPayload(Left("someEndpoint1"))
                )
              )
            ),
            Some("DELETE"),
            Some(true)
          )
        ),
        ("someEndpoint1/", MapperRecordPayload(MappingPayload(Left("group0101")), Some("DELETE"), Some(false))),
        ("someEndpoint1/", MapperRecordPayload(MappingPayload(Left("group0101")), Some("POST"), Some(false))),
        ("another/route/{{some_id}}", MapperRecordPayload(MappingPayload(Left("group1")), Some("PUT"), Some(false))),
        ("someEndpoint2/", MapperRecordPayload(MappingPayload(Left("group11114")), Some("POST"), Some(false))),
        ("someEndpoint2/", MapperRecordPayload(MappingPayload(Left("group11115")), Some("DELETE"), Some(false))),
        ("someEndpoint4/", MapperRecordPayload(MappingPayload(Left("group11116")), Some("PATCH"), Some(false))),
        (
          "/someEndpoint3/",
          MapperRecordPayload(
            MappingPayload(
              Right(
                Map(
                  "property1" -> MappingPayload(Left("someEndpointNESTED")),
                  "property2" -> MappingPayload(Left("someEndpointNESTEDThatDoesNotExist")),
                  "property3" -> MappingPayload(
                    Right(
                      Map(
                        "property4" -> MappingPayload(
                          Right(Map("property5" -> MappingPayload(Left("someEndpoint3"))))
                        ), // circular - it's not valid, but search should still work
                        "property6" -> MappingPayload(Left("someEndpointNESTED2"))
                      )
                    )
                  )
                )
              )
            ),
            Some("POST"),
            Some(true)
          )
        ),
        ("someEndpointNESTED", MapperRecordPayload(MappingPayload(Left("groupNested")), Some("POST"), Some(false))),
        ("someEndpointNESTED", MapperRecordPayload(MappingPayload(Left("groupNested1")), Some("DELETE"), Some(false))),
        ("someEndpointNESTED2", MapperRecordPayload(MappingPayload(Left("groupNested2")), Some("POST"), Some(true))),
        ("someEndpointNESTED2", MapperRecordPayload(MappingPayload(Left("groupNested2")), Some("GET"), Some(true)))
      )
    ),
    prefixes = Some(
      Map.from(
        List(
          ("group1", "api/v1"),
          ("group2/", "api/v2"),
          ("group3", "api/v3"),
          ("groupNested", "api/v4/")
        )
      )
    )
  )

  "RoutesServiceImpl#findAll" should "return all entries (mappers and prefixes) saved with RoutesServiceImpl#save" in {
    val f = (routesService.save(payload1)
      >> routesService.save(payload2)
      >> routesService.findAll()).runToFuture

    whenReady(f) { res =>
      res.mappers.get should contain theSameElementsAs expectedWithoutOverrides.mappers.get
      res.prefixes should be(expectedWithoutOverrides.prefixes)
    }
  }

  it should "be able to filter out based on StandaloneFilter#StandaloneOnly" in {
    val f = (routesService.save(payload1)
      >> routesService.save(payload2)
      >> routesService.findAll(StandaloneFilter.StandaloneOnly)).runToFuture

    whenReady(f) { res =>
      res.mappers.get should contain theSameElementsAs expectedWithoutOverridesForStandaloneOnly.mappers.get
      res.prefixes should be(expectedWithoutOverridesForStandaloneOnly.prefixes)
    }
  }

  it should "be able to filter out based on StandaloneFilter#NonStandaloneOnly" in {
    val f = (routesService.save(payload1)
      >> routesService.save(payload2)
      >> routesService.findAll(StandaloneFilter.NonStandaloneOnly)).runToFuture

    whenReady(f) { res =>
      res.mappers.get should contain theSameElementsAs expectedWithoutOverridesForNonStandaloneOnly.mappers.get
      res.prefixes should be(expectedWithoutOverridesForNonStandaloneOnly.prefixes)
    }
  }

  it should "return all entries (mappers and prefixes) saved with RoutesServiceImpl#saveWithOverrides" in {
    val f = (routesService.saveWithOverrides(payload1)
      >> routesService.saveWithOverrides(payload2)
      >> routesService.findAll()).runToFuture

    whenReady(f) { res =>
      res.mappers.get should contain theSameElementsAs expectedWithOverrides.mappers.get
      res.prefixes should be(expectedWithOverrides.prefixes)
    }
  }

  "RoutesServiceImpl#findAllMappers" should "return all mappers saved with RoutesServiceImpl#save" in {
    val f = (routesService.save(payload1)
      >> routesService.save(payload2)
      >> routesService.findAllMappers()).runToFuture

    whenReady(f) { res =>
      res should contain theSameElementsAs expectedWithoutOverrides.mappers.get
    }
  }

  it should "be able to filter out based on StandaloneFilter#StandaloneOnly" in {
    val f = (routesService.save(payload1)
      >> routesService.save(payload2)
      >> routesService.findAllMappers(StandaloneFilter.StandaloneOnly)).runToFuture

    whenReady(f) { res =>
      res should contain theSameElementsAs expectedWithoutOverridesForStandaloneOnly.mappers.get
    }
  }

  it should "be able to filter out based on StandaloneFilter#NonStandaloneOnly" in {
    val f = (routesService.save(payload1)
      >> routesService.save(payload2)
      >> routesService.findAllMappers(StandaloneFilter.NonStandaloneOnly)).runToFuture

    whenReady(f) { res =>
      res should contain theSameElementsAs expectedWithoutOverridesForNonStandaloneOnly.mappers.get
    }
  }

  it should "return all mappers saved with RoutesServiceImpl#saveWithOverrides" in {
    val f = (routesService.saveWithOverrides(payload1)
      >> routesService.saveWithOverrides(payload2)
      >> routesService.findAllMappers()).runToFuture

    whenReady(f) { res =>
      res should contain theSameElementsAs expectedWithOverrides.mappers.get
    }
  }

  "RoutesServiceImpl#findAllPrefixes" should "return all prefixes saved with RoutesServiceImpl#save" in {
    val f = (routesService.save(payload1)
      >> routesService.save(payload2)
      >> routesService.findAllPrefixes()).runToFuture

    whenReady(f) { res => res should be(expectedWithoutOverrides.prefixes.get) }
  }

  it should "return all prefixes saved with RoutesServiceImpl#saveWithOverrides" in {
    val f = (routesService.save(payload1)
      >> routesService.saveWithOverrides(payload2)
      >> routesService.findAllPrefixes()).runToFuture

    whenReady(f) { res => res should be(expectedWithOverrides.prefixes.get) }
  }

  "RoutesServiceImpl#delete" should " remove from persistence layer requested mappers and prefixes" in {
    val f = (routesService.save(payload1)
      >> routesService.save(payload2)
      >> routesService
        .delete(
          RoutesRemovePayload(
            mappers = Some(
              List(
                MapperRemovePayload("/route/one", Some("Post")), // valid
                MapperRemovePayload("/route/one", Some("Delete")), // not exist
                MapperRemovePayload("/another/route/{{some_id}}", Some("put")), // valid
                MapperRemovePayload("groupRoute/**", Some("GET")), // valid
                MapperRemovePayload("not/exist/route", Some("Get")), // not exist
                MapperRemovePayload("/route/two", Some("Post")) // not exist (wrong http method)
              )
            ),
            prefixes = Some(
              List(
                "group1", // valid
                "  group3 ", // valid (it should be trimmed)
                "group1313" // not exist
              )
            )
          )
        )
        .flatMap(deleteRes => routesService.findAll().map(r => (deleteRes, r)))).runToFuture

    whenReady(f) { res =>
      val deleteRes = res._1
      val findAllRes = res._2

      val deleteResMappers = deleteRes._1
      val deleteResPrefixes = deleteRes._2

      // mappers
      deleteResMappers.filter(_.isRight).map(_.getOrElse("")) should contain theSameElementsAs List(
        "route/one",
        "another/route/{{some_id}}",
        "groupRoute/**"
      )
      deleteResMappers.filter(_.isLeft).map(_.swap.getOrElse("")) should contain theSameElementsAs List(
        NotExists("Route '/route/one' [DELETE] does not exist"), // routes inside info are not escaped
        NotExists("Route 'not/exist/route' [GET] does not exist"),
        NotExists("Route '/route/two' [POST] does not exist")
      )

      // prefixes
      deleteResPrefixes.filter(_.isRight).map(_.getOrElse("")) should contain theSameElementsAs List("group1", "group3")
      deleteResPrefixes.filter(_.isLeft).map(_.swap.getOrElse("")) should contain theSameElementsAs List(
        NotExists("There is no prefix associated with the group 'group1313'")
      )

      findAllRes.mappers.get should contain theSameElementsAs List(
        ("another/route/{{some_id}}", MapperRecordPayload(MappingPayload(Left("group1")), Some("GET"), Some(true))),
        (
          "route/two",
          MapperRecordPayload(
            MappingPayload(
              Right(
                Map(
                  "property1" -> MappingPayload(Left("someEndpoint1"))
                )
              )
            ),
            Some("DELETE"),
            Some(true)
          )
        )
      )
      findAllRes.prefixes should be(
        Some(
          Map.from(
            List(
              ("group2", "api/v2")
            )
          )
        )
      )
    }
  }

  "RoutesServiceImpl#findByGroup" should "find all related mappers and a prefix if exists" in {
    val f = (
      routesService.save(findByGroupPayload) >>
        Task.sequence(
          List(
            routesService
              .findByGroup(groupName = "group1")
              .map(res =>
                (
                  res,
                  RoutesResourcePayload(
                    mappers = Some(
                      List(
                        (
                          "another/route/{{some_id}}",
                          MapperRecordPayload(MappingPayload(Left("group1")), Some("GET"), Some(true))
                        ),
                        (
                          "another/route/{{some_id}}",
                          MapperRecordPayload(MappingPayload(Left("group1")), Some("PUT"), Some(false))
                        )
                      )
                    ),
                    prefixes = Some(
                      Map.from(
                        List(
                          ("group1", "api/v1")
                        )
                      )
                    )
                  )
                )
              ),
            routesService
              .findByGroup(groupName = "group2/")
              .map(res =>
                (
                  res,
                  RoutesResourcePayload(
                    mappers = Some(
                      List(
                        (
                          "groupRoute/**",
                          MapperRecordPayload(MappingPayload(Left("group2/")), Some("GET"), Some(false))
                        )
                      )
                    ),
                    prefixes = Some(
                      Map.from(
                        List(
                          ("group2/", "api/v2")
                        )
                      )
                    )
                  )
                )
              ),
            routesService
              .findByGroup(groupName = "group0101")
              .map(res =>
                (
                  res,
                  RoutesResourcePayload(
                    mappers = Some(
                      List(
                        (
                          "someEndpoint1",
                          MapperRecordPayload(MappingPayload(Left("group0101")), Some("DELETE"), Some(false))
                        ),
                        (
                          "someEndpoint1",
                          MapperRecordPayload(MappingPayload(Left("group0101")), Some("POST"), Some(false))
                        ),
                        (
                          "route/two",
                          MapperRecordPayload(
                            MappingPayload(
                              Right(
                                Map(
                                  "property1" -> MappingPayload(Left("someEndpoint1"))
                                )
                              )
                            ),
                            Some("DELETE"),
                            Some(true)
                          )
                        ),
                        (
                          "route/one",
                          MapperRecordPayload(
                            MappingPayload(
                              Right(
                                Map(
                                  "property1" -> MappingPayload(Left("someEndpoint1")),
                                  "property2" -> MappingPayload(
                                    Right(Map("property22" -> MappingPayload(Left("someEndpoint2"))))
                                  ),
                                  "property3" -> MappingPayload(
                                    Right(
                                      Map(
                                        "property4" -> MappingPayload(
                                          Right(Map("property5" -> MappingPayload(Left("someEndpoint3"))))
                                        ),
                                        "property6" -> MappingPayload(Left("someEndpoint4")),
                                        "property7" -> MappingPayload(Left("someEndpoint1"))
                                      )
                                    )
                                  )
                                )
                              )
                            ),
                            Some("POST"),
                            Some(true)
                          )
                        )
                      )
                    ),
                    prefixes = Some(Map.empty)
                  )
                )
              ),
            routesService
              .findByGroup(groupName = "group11114")
              .map(res =>
                (
                  res,
                  RoutesResourcePayload(
                    mappers = Some(
                      List(
                        (
                          "someEndpoint2",
                          MapperRecordPayload(MappingPayload(Left("group11114")), Some("POST"), Some(false))
                        ),
                        (
                          "route/one",
                          MapperRecordPayload(
                            MappingPayload(
                              Right(
                                Map(
                                  "property1" -> MappingPayload(Left("someEndpoint1")),
                                  "property2" -> MappingPayload(
                                    Right(Map("property22" -> MappingPayload(Left("someEndpoint2"))))
                                  ),
                                  "property3" -> MappingPayload(
                                    Right(
                                      Map(
                                        "property4" -> MappingPayload(
                                          Right(Map("property5" -> MappingPayload(Left("someEndpoint3"))))
                                        ),
                                        "property6" -> MappingPayload(Left("someEndpoint4")),
                                        "property7" -> MappingPayload(Left("someEndpoint1"))
                                      )
                                    )
                                  )
                                )
                              )
                            ),
                            Some("POST"),
                            Some(true)
                          )
                        )
                      )
                    ),
                    prefixes = Some(Map.empty)
                  )
                )
              ),
            routesService
              .findByGroup(groupName = "group11115")
              .map(res =>
                (
                  res,
                  RoutesResourcePayload(
                    mappers = Some(
                      List(
                        (
                          "someEndpoint2",
                          MapperRecordPayload(MappingPayload(Left("group11115")), Some("DELETE"), Some(false))
                        )
                      )
                    ),
                    prefixes = Some(Map.empty)
                  )
                )
              ),
            routesService
              .findByGroup(groupName = "group11116")
              .map(res =>
                (
                  res,
                  RoutesResourcePayload(
                    mappers = Some(
                      List(
                        (
                          "someEndpoint4",
                          MapperRecordPayload(MappingPayload(Left("group11116")), Some("PATCH"), Some(false))
                        )
                      )
                    ),
                    prefixes = Some(Map.empty)
                  )
                )
              ),
            routesService
              .findByGroup(groupName = "groupNested")
              .map(res =>
                (
                  res,
                  RoutesResourcePayload(
                    mappers = Some(
                      List(
                        (
                          "someEndpointNESTED",
                          MapperRecordPayload(MappingPayload(Left("groupNested")), Some("POST"), Some(false))
                        ),
                        (
                          "someEndpoint3",
                          MapperRecordPayload(
                            MappingPayload(
                              Right(
                                Map(
                                  "property1" -> MappingPayload(Left("someEndpointNESTED")),
                                  "property2" -> MappingPayload(Left("someEndpointNESTEDThatDoesNotExist")),
                                  "property3" -> MappingPayload(
                                    Right(
                                      Map(
                                        "property4" -> MappingPayload(
                                          Right(Map("property5" -> MappingPayload(Left("someEndpoint3"))))
                                        ), // circular - it's not valid, but search should still work
                                        "property6" -> MappingPayload(Left("someEndpointNESTED2"))
                                      )
                                    )
                                  )
                                )
                              )
                            ),
                            Some("POST"),
                            Some(true)
                          )
                        ),
                        (
                          "another/route/{{some_id}}/complex",
                          MapperRecordPayload(
                            MappingPayload(
                              Right(
                                Map(
                                  "property3" -> MappingPayload(
                                    Right(
                                      Map(
                                        "property4" -> MappingPayload(
                                          Right(Map("property5" -> MappingPayload(Left("someEndpoint3"))))
                                        )
                                      )
                                    )
                                  )
                                )
                              )
                            ),
                            Some("POST"),
                            Some(true)
                          )
                        ),
                        (
                          "route/one",
                          MapperRecordPayload(
                            MappingPayload(
                              Right(
                                Map(
                                  "property1" -> MappingPayload(Left("someEndpoint1")),
                                  "property2" -> MappingPayload(
                                    Right(Map("property22" -> MappingPayload(Left("someEndpoint2"))))
                                  ),
                                  "property3" -> MappingPayload(
                                    Right(
                                      Map(
                                        "property4" -> MappingPayload(
                                          Right(Map("property5" -> MappingPayload(Left("someEndpoint3"))))
                                        ),
                                        "property6" -> MappingPayload(Left("someEndpoint4")),
                                        "property7" -> MappingPayload(Left("someEndpoint1"))
                                      )
                                    )
                                  )
                                )
                              )
                            ),
                            Some("POST"),
                            Some(true)
                          )
                        )
                      )
                    ),
                    prefixes = Some(
                      Map.from(
                        List(
                          ("groupNested", "api/v4")
                        )
                      )
                    )
                  )
                )
              ),
            routesService
              .findByGroup(groupName = "groupNested1")
              .map(res =>
                (
                  res,
                  RoutesResourcePayload(
                    mappers = Some(
                      List(
                        (
                          "someEndpointNESTED",
                          MapperRecordPayload(MappingPayload(Left("groupNested1")), Some("DELETE"), Some(false))
                        )
                      )
                    ),
                    prefixes = Some(Map.empty)
                  )
                )
              ),
            routesService
              .findByGroup(groupName = "groupNested2")
              .map(res =>
                (
                  res,
                  RoutesResourcePayload(
                    mappers = Some(
                      List(
                        (
                          "someEndpointNESTED2",
                          MapperRecordPayload(MappingPayload(Left("groupNested2")), Some("POST"), Some(true))
                        ),
                        (
                          "someEndpoint3",
                          MapperRecordPayload(
                            MappingPayload(
                              Right(
                                Map(
                                  "property1" -> MappingPayload(Left("someEndpointNESTED")),
                                  "property2" -> MappingPayload(Left("someEndpointNESTEDThatDoesNotExist")),
                                  "property3" -> MappingPayload(
                                    Right(
                                      Map(
                                        "property4" -> MappingPayload(
                                          Right(Map("property5" -> MappingPayload(Left("someEndpoint3"))))
                                        ), // circular - it's not valid, but search should still work
                                        "property6" -> MappingPayload(Left("someEndpointNESTED2"))
                                      )
                                    )
                                  )
                                )
                              )
                            ),
                            Some("POST"),
                            Some(true)
                          )
                        ),
                        (
                          "another/route/{{some_id}}/complex",
                          MapperRecordPayload(
                            MappingPayload(
                              Right(
                                Map(
                                  "property3" -> MappingPayload(
                                    Right(
                                      Map(
                                        "property4" -> MappingPayload(
                                          Right(Map("property5" -> MappingPayload(Left("someEndpoint3"))))
                                        )
                                      )
                                    )
                                  )
                                )
                              )
                            ),
                            Some("POST"),
                            Some(true)
                          )
                        ),
                        (
                          "route/one",
                          MapperRecordPayload(
                            MappingPayload(
                              Right(
                                Map(
                                  "property1" -> MappingPayload(Left("someEndpoint1")),
                                  "property2" -> MappingPayload(
                                    Right(Map("property22" -> MappingPayload(Left("someEndpoint2"))))
                                  ),
                                  "property3" -> MappingPayload(
                                    Right(
                                      Map(
                                        "property4" -> MappingPayload(
                                          Right(Map("property5" -> MappingPayload(Left("someEndpoint3"))))
                                        ),
                                        "property6" -> MappingPayload(Left("someEndpoint4")),
                                        "property7" -> MappingPayload(Left("someEndpoint1"))
                                      )
                                    )
                                  )
                                )
                              )
                            ),
                            Some("POST"),
                            Some(true)
                          )
                        ),
                        (
                          "someEndpointNESTED2",
                          MapperRecordPayload(MappingPayload(Left("groupNested2")), Some("GET"), Some(true))
                        )
                      )
                    ),
                    prefixes = Some(Map.empty)
                  )
                )
              ),
            routesService
              .findByGroup(groupName = "group3")
              .map(res =>
                (
                  res,
                  RoutesResourcePayload(
                    mappers = Some(List.empty),
                    prefixes = Some(Map("group3" -> "api/v3"))
                  )
                )
              ),
            routesService
              .findByGroup(groupName = "notExists")
              .map(res =>
                (
                  res,
                  RoutesResourcePayload(
                    mappers = Some(List.empty),
                    prefixes = Some(Map.empty)
                  )
                )
              )
          )
        )
    ).runToFuture

    whenReady(f) { res =>
      res.foreach(r => {
        r._1.mappers.get should contain theSameElementsAs r._2.mappers.get
        r._1.prefixes should be(r._2.prefixes)
      })
    }
    succeed
  }

  it should "find all related mappers and a prefix if exists (standalone filter use cases)" in {
    val f = (
      routesService.save(findByGroupPayload) >>
        Task.sequence(
          List(
            routesService
              .findByGroup(groupName = "group1", standaloneFilter = StandaloneFilter.StandaloneOnly)
              .map(res =>
                (
                  res,
                  RoutesResourcePayload(
                    mappers = Some(
                      List(
                        (
                          "another/route/{{some_id}}",
                          MapperRecordPayload(MappingPayload(Left("group1")), Some("GET"), Some(true))
                        )
                      )
                    ),
                    prefixes = Some(
                      Map.from(
                        List(
                          ("group1", "api/v1")
                        )
                      )
                    )
                  )
                )
              ),
            routesService
              .findByGroup(groupName = "group2/", standaloneFilter = StandaloneFilter.StandaloneOnly)
              .map(res =>
                (
                  res,
                  RoutesResourcePayload(
                    mappers = Some(List.empty),
                    prefixes = Some(
                      Map.from(
                        List(
                          ("group2/", "api/v2")
                        )
                      )
                    )
                  )
                )
              ),
            routesService
              .findByGroup(groupName = "group0101", standaloneFilter = StandaloneFilter.NonStandaloneOnly)
              .map(res =>
                (
                  res,
                  RoutesResourcePayload(
                    mappers = Some(
                      List(
                        (
                          "someEndpoint1",
                          MapperRecordPayload(MappingPayload(Left("group0101")), Some("DELETE"), Some(false))
                        ),
                        (
                          "someEndpoint1",
                          MapperRecordPayload(MappingPayload(Left("group0101")), Some("POST"), Some(false))
                        )
                      )
                    ),
                    prefixes = Some(Map.empty)
                  )
                )
              ),
            routesService
              .findByGroup(groupName = "group11114", standaloneFilter = StandaloneFilter.StandaloneOnly)
              .map(res =>
                (
                  res,
                  RoutesResourcePayload(
                    mappers = Some(
                      List(
                        (
                          "route/one",
                          MapperRecordPayload(
                            MappingPayload(
                              Right(
                                Map(
                                  "property1" -> MappingPayload(Left("someEndpoint1")),
                                  "property2" -> MappingPayload(
                                    Right(Map("property22" -> MappingPayload(Left("someEndpoint2"))))
                                  ),
                                  "property3" -> MappingPayload(
                                    Right(
                                      Map(
                                        "property4" -> MappingPayload(
                                          Right(Map("property5" -> MappingPayload(Left("someEndpoint3"))))
                                        ),
                                        "property6" -> MappingPayload(Left("someEndpoint4")),
                                        "property7" -> MappingPayload(Left("someEndpoint1"))
                                      )
                                    )
                                  )
                                )
                              )
                            ),
                            Some("POST"),
                            Some(true)
                          )
                        )
                      )
                    ),
                    prefixes = Some(Map.empty)
                  )
                )
              )
          )
        )
    ).runToFuture

    whenReady(f) { res =>
      res.foreach(r => {
        r._1.mappers.get should contain theSameElementsAs r._2.mappers.get
        r._1.prefixes should be(r._2.prefixes)
      })
    }
    succeed
  }
}
