package com.github.pandafolks.panda.routes

import cats.effect.concurrent.Ref
import com.github.pandafolks.panda.backgroundjobsregistry.InMemoryBackgroundJobsRegistryImpl
import com.github.pandafolks.panda.routes.RoutesTree.RouteInfo
import com.github.pandafolks.panda.routes.entity.MappingContent
import com.github.pandafolks.panda.routes.payload.{MapperRecordPayload, MappingPayload, RoutesResourcePayload}
import monix.execution.Scheduler
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, EitherValues, PrivateMethodTester}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import monix.eval.Task
import monix.execution.schedulers.SchedulerService
import org.http4s.Method
import org.http4s.Uri.Path

import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class TreesServiceImplItTest
    extends AsyncFlatSpec
    with RoutesFixture
    with Matchers
    with ScalaFutures
    with EitherValues
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with PrivateMethodTester {
  implicit val scheduler: SchedulerService = Scheduler.forkJoin(Runtime.getRuntime.availableProcessors() * 2, Runtime.getRuntime.availableProcessors() * 2)

  implicit val defaultConfig: PatienceConfig = PatienceConfig(30.seconds, 100.milliseconds)

  override protected def afterAll(): Unit = mongoContainer.stop()

  override protected def beforeEach(): Unit = Await.result(
    mappersAndPrefixesConnection.use { case (mappers, prefixes) =>
      mappers.db.dropCollection(dbName, mappersColName) >> prefixes.db.dropCollection(dbName, prefixesColName)
    }.runToFuture,
    5.seconds
  )

  private def createTreesServiceImpl(): Task[TreesService] =
    TreesServiceImpl(mapperDao = mapperDao, prefixDao = prefixDao, new InMemoryBackgroundJobsRegistryImpl(scheduler))(
      mappersAndPrefixesConnection
    )(0) // background job turned off

  private val payload1 = RoutesResourcePayload(
    mappers = Some(
      List(
        (
          "/route/one",
          MapperRecordPayload(
            MappingPayload(
              Right(
                Map(
                  "property1" -> MappingPayload(Left("/someEndpoint1/")),
                  "property2" -> MappingPayload(Right(Map("property22" -> MappingPayload(Left("someEndpoint2")))))
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
          ) // groupName can contain slashes
        ),
        ("/another/route/{{some_id}}", MapperRecordPayload(MappingPayload(Left("group1")), Some("put"), Some(false))),
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
        )
      )
    ),
    prefixes = Some(
      Map.from(
        List(
          ("group1 ", "/api/v1"),
          ("group2", "api/v2/"),
          ("group3/", "/api/v3/") // groupName can contain slashes
        )
      )
    )
  )

  "TreesServiceImpl#findRoute" should "return RouteInfo with extracted wildcard params if the one exists" in {
    val f = (
      routesService.save(payload1) >>
        createTreesServiceImpl().flatMap(treesService =>
          Task.parSequence(
            List(
              treesService
                .findRoute(Path.unsafeFromString("route/one"), Method.POST)
                .map(res =>
                  (
                    res,
                    (
                      RouteInfo(
                        MappingContent(
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
                              )
                            )
                          )
                        ),
                        isPocket = false,
                        isStandalone = true
                      ),
                      Map.empty
                    )
                  )
                ),
              treesService
                .findRoute(Path.unsafeFromString("/another/route/some_id123"), Method.GET)
                .map(res =>
                  (
                    res,
                    (
                      RouteInfo(
                        MappingContent(left = Some("group1"), right = Option.empty),
                        isPocket = false,
                        isStandalone = true
                      ),
                      Map("some_id" -> "some_id123")
                    )
                  )
                ),
              treesService
                .findRoute(Path.unsafeFromString("groupRoute/pathParam1/pathParam2"), Method.GET)
                .map(res =>
                  (
                    res,
                    (
                      RouteInfo(
                        MappingContent(left = Some("group2/"), right = Option.empty),
                        isPocket = true,
                        isStandalone = false
                      ),
                      Map.empty
                    )
                  )
                ),
              treesService
                .findRoute(Path.unsafeFromString("/another/route/some_id222/"), Method.PUT)
                .map(res =>
                  (
                    res,
                    (
                      RouteInfo(
                        MappingContent(left = Some("group1"), right = Option.empty),
                        isPocket = false,
                        isStandalone = false
                      ),
                      Map("some_id" -> "some_id222")
                    )
                  )
                ),
              treesService
                .findRoute(Path.unsafeFromString("/route/two/"), Method.DELETE)
                .map(res =>
                  (
                    res,
                    (
                      RouteInfo(
                        MappingContent(
                          left = Option.empty,
                          right = Some(
                            Map(
                              "property1" -> MappingContent(left = Some("someEndpoint1"), right = Option.empty)
                            )
                          )
                        ),
                        isPocket = false,
                        isStandalone = true
                      ),
                      Map.empty
                    )
                  )
                )
            )
          )
        )
    ).runToFuture

    whenReady(f) { res =>
      res.foreach(tuple => tuple._1.get should be(tuple._2))
      succeed
    }
  }

  it should "return None if there is no route for a requested path and method" in {
    val f = (
      routesService.save(payload1) >>
        createTreesServiceImpl().flatMap(treesService =>
          Task.parSequence(
            List(
              treesService.findRoute(Path.unsafeFromString("route/one"), Method.GET),
              treesService.findRoute(Path.unsafeFromString("/another/route/some_id123/some_extra"), Method.GET),
              treesService.findRoute(Path.unsafeFromString("groupRoute/pathParam1/pathParam2"), Method.PATCH),
              treesService.findRoute(Path.unsafeFromString("/another/route4/some_id222/"), Method.PUT),
              treesService.findRoute(Path.unsafeFromString("/route/two/extra/param"), Method.DELETE)
            )
          )
        )
    ).runToFuture

    whenReady(f) { res =>
      res.foreach(_ should be(None))
      succeed
    }
  }

  "TreesServiceImpl#findStandaloneRoute" should "return RouteInfo with extracted wildcard params if the standalone one exists" in {
    val f = (
      routesService.save(payload1) >>
        createTreesServiceImpl().flatMap(treesService =>
          Task.parSequence(
            List(
              treesService
                .findStandaloneRoute(Path.unsafeFromString("route/one"), Method.POST)
                .map(res =>
                  (
                    res,
                    Some(
                      (
                        RouteInfo(
                          MappingContent(
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
                                )
                              )
                            )
                          ),
                          isPocket = false,
                          isStandalone = true
                        ),
                        Map.empty
                      )
                    )
                  )
                ),
              treesService
                .findStandaloneRoute(Path.unsafeFromString("/another/route/some_id123"), Method.GET)
                .map(res =>
                  (
                    res,
                    Some(
                      (
                        RouteInfo(
                          MappingContent(left = Some("group1"), right = Option.empty),
                          isPocket = false,
                          isStandalone = true
                        ),
                        Map("some_id" -> "some_id123")
                      )
                    )
                  )
                ),
              treesService
                .findStandaloneRoute(Path.unsafeFromString("groupRoute/pathParam1/pathParam2"), Method.GET)
                .map(res => (res, Option.empty)),
              treesService
                .findStandaloneRoute(Path.unsafeFromString("/another/route/some_id222/"), Method.PUT)
                .map(res => (res, Option.empty)),
              treesService
                .findStandaloneRoute(Path.unsafeFromString("/route/two/"), Method.DELETE)
                .map(res =>
                  (
                    res,
                    Some(
                      (
                        RouteInfo(
                          MappingContent(
                            left = Option.empty,
                            right = Some(
                              Map(
                                "property1" -> MappingContent(left = Some("someEndpoint1"), right = Option.empty)
                              )
                            )
                          ),
                          isPocket = false,
                          isStandalone = true
                        ),
                        Map.empty
                      )
                    )
                  )
                )
            )
          )
        )
    ).runToFuture

    whenReady(f) { res =>
      res.foreach(tuple => tuple._1 should be(tuple._2))
      succeed
    }
  }

  "TreesServiceImpl#findPrefix" should "return a defined Prefix path if one exists. Otherwise, return empty path" in {
    val f = (
      routesService.save(payload1) >>
        createTreesServiceImpl().flatMap(treesService =>
          Task.parSequence(
            List(
              treesService
                .findPrefix(Group("group1"))
                .map(res => (res, Path.unsafeFromString("api/v1"))),
              treesService
                .findPrefix(Group("notKnownGroup"))
                .map(res => (res, Path.empty)),
              treesService
                .findPrefix(Group("group2"))
                .map(res => (res, Path.unsafeFromString("api/v2"))),
              treesService
                .findPrefix(Group("group3/"))
                .map(res => (res, Path.unsafeFromString("api/v3"))),
              treesService
                .findPrefix(Group("OneMore/notKnownGroup"))
                .map(res => (res, Path.empty))
            )
          )
        )
    ).runToFuture

    whenReady(f) { res =>
      res.foreach(r => r._1 should be(r._2))
      succeed
    }
  }

  "TreesServiceImpl#reloadTreesIfNecessary" should "reload trees and prefixes if both mappers and prefixes data changed" in {
    val oldLatestSeenMappingTimestamp = new AtomicLong(0L)
    val oldLatestSeenPrefixTimestamp = new AtomicLong(0L)
    val foundManuallyNewLatestSeenMappingTimestamp = new AtomicLong(0L)
    val foundManuallyNewLatestSeenPrefixTimestamp = new AtomicLong(0L)

    val reloadTreesIfNecessaryMethod = PrivateMethod[Task[Unit]](Symbol("reloadTreesIfNecessary"))
    val latestSeenMappingTimestamp = PrivateMethod[Ref[Task, Long]](Symbol("latestSeenMappingTimestamp"))
    val latestSeenPrefixTimestamp = PrivateMethod[Ref[Task, Long]](Symbol("latestSeenPrefixTimestamp"))

    val f = (
      routesService.save(payload1) >>
        createTreesServiceImpl().flatMap(treesService =>
          treesService.invokePrivate(latestSeenMappingTimestamp()).get.map(v => oldLatestSeenMappingTimestamp.set(v)) >>
            treesService.invokePrivate(latestSeenPrefixTimestamp()).get.map(v => oldLatestSeenPrefixTimestamp.set(v)) >>
            routesService.saveWithOverrides(
              payload1
            ) >> // same data, but overrides perform save on all entries so the timestamp got updated
            mappersAndPrefixesConnection.use { case (mappersOperator, prefixesOperator) =>
              mapperDao
                .findAll(mappersOperator)
                .toListL
                .map(r => foundManuallyNewLatestSeenMappingTimestamp.set(r.map(_.lastUpdateTimestamp).max)) >>
                prefixDao
                  .findAll(prefixesOperator)
                  .toListL
                  .map(r => foundManuallyNewLatestSeenPrefixTimestamp.set(r.map(_.lastUpdateTimestamp).max))
            } >>
            treesService.invokePrivate(reloadTreesIfNecessaryMethod()) >>
            Task.parZip2(
              treesService.invokePrivate(latestSeenMappingTimestamp()).get,
              treesService.invokePrivate(latestSeenPrefixTimestamp()).get
            )
        )
    ).runToFuture

    whenReady(f) { res =>
      oldLatestSeenMappingTimestamp.get() should be < foundManuallyNewLatestSeenMappingTimestamp.get()
      oldLatestSeenMappingTimestamp.get() should be < res._1
      foundManuallyNewLatestSeenMappingTimestamp.get() should be(res._1)

      oldLatestSeenPrefixTimestamp.get() should be < foundManuallyNewLatestSeenPrefixTimestamp.get()
      oldLatestSeenPrefixTimestamp.get() should be < res._2
      foundManuallyNewLatestSeenPrefixTimestamp.get() should be(res._2)
    }
  }

  it should "reload only trees if the mappers data changed but the prefixes data did not" in {
    val oldLatestSeenMappingTimestamp = new AtomicLong(0L)
    val oldLatestSeenPrefixTimestamp = new AtomicLong(0L)
    val foundManuallyNewLatestSeenMappingTimestamp = new AtomicLong(0L)
    val foundManuallyNewLatestSeenPrefixTimestamp = new AtomicLong(0L)

    val reloadTreesIfNecessaryMethod = PrivateMethod[Task[Unit]](Symbol("reloadTreesIfNecessary"))
    val latestSeenMappingTimestamp = PrivateMethod[Ref[Task, Long]](Symbol("latestSeenMappingTimestamp"))
    val latestSeenPrefixTimestamp = PrivateMethod[Ref[Task, Long]](Symbol("latestSeenPrefixTimestamp"))

    val f = (
      routesService.save(payload1) >>
        createTreesServiceImpl().flatMap(treesService =>
          treesService.invokePrivate(latestSeenMappingTimestamp()).get.map(v => oldLatestSeenMappingTimestamp.set(v)) >>
            treesService.invokePrivate(latestSeenPrefixTimestamp()).get.map(v => oldLatestSeenPrefixTimestamp.set(v)) >>
            routesService.saveWithOverrides(
              RoutesResourcePayload(mappers = payload1.mappers, prefixes = Option.empty)
            ) >>
            mappersAndPrefixesConnection.use { case (mappersOperator, prefixesOperator) =>
              mapperDao
                .findAll(mappersOperator)
                .toListL
                .map(r => foundManuallyNewLatestSeenMappingTimestamp.set(r.map(_.lastUpdateTimestamp).max)) >>
                prefixDao
                  .findAll(prefixesOperator)
                  .toListL
                  .map(r => foundManuallyNewLatestSeenPrefixTimestamp.set(r.map(_.lastUpdateTimestamp).max))
            } >>
            treesService.invokePrivate(reloadTreesIfNecessaryMethod()) >>
            Task.parZip2(
              treesService.invokePrivate(latestSeenMappingTimestamp()).get,
              treesService.invokePrivate(latestSeenPrefixTimestamp()).get
            )
        )
    ).runToFuture

    whenReady(f) { res =>
      oldLatestSeenMappingTimestamp.get() should be < foundManuallyNewLatestSeenMappingTimestamp.get()
      oldLatestSeenMappingTimestamp.get() should be < res._1
      foundManuallyNewLatestSeenMappingTimestamp.get() should be(res._1)

      oldLatestSeenPrefixTimestamp.get() should be(foundManuallyNewLatestSeenPrefixTimestamp.get())
      oldLatestSeenPrefixTimestamp.get() should be(res._2)
    }
  }

  it should "reload only prefixes if the prefixes data changed but the mappers data did not" in {
    val oldLatestSeenMappingTimestamp = new AtomicLong(0L)
    val oldLatestSeenPrefixTimestamp = new AtomicLong(0L)
    val foundManuallyNewLatestSeenMappingTimestamp = new AtomicLong(0L)
    val foundManuallyNewLatestSeenPrefixTimestamp = new AtomicLong(0L)

    val reloadTreesIfNecessaryMethod = PrivateMethod[Task[Unit]](Symbol("reloadTreesIfNecessary"))
    val latestSeenMappingTimestamp = PrivateMethod[Ref[Task, Long]](Symbol("latestSeenMappingTimestamp"))
    val latestSeenPrefixTimestamp = PrivateMethod[Ref[Task, Long]](Symbol("latestSeenPrefixTimestamp"))

    val f = (
      routesService.save(payload1) >>
        createTreesServiceImpl().flatMap(treesService =>
          treesService.invokePrivate(latestSeenMappingTimestamp()).get.map(v => oldLatestSeenMappingTimestamp.set(v)) >>
            treesService.invokePrivate(latestSeenPrefixTimestamp()).get.map(v => oldLatestSeenPrefixTimestamp.set(v)) >>
            routesService.saveWithOverrides(
              RoutesResourcePayload(mappers = Option.empty, prefixes = payload1.prefixes)
            ) >>
            mappersAndPrefixesConnection.use { case (mappersOperator, prefixesOperator) =>
              mapperDao
                .findAll(mappersOperator)
                .toListL
                .map(r => foundManuallyNewLatestSeenMappingTimestamp.set(r.map(_.lastUpdateTimestamp).max)) >>
                prefixDao
                  .findAll(prefixesOperator)
                  .toListL
                  .map(r => foundManuallyNewLatestSeenPrefixTimestamp.set(r.map(_.lastUpdateTimestamp).max))
            } >>
            treesService.invokePrivate(reloadTreesIfNecessaryMethod()) >>
            Task.parZip2(
              treesService.invokePrivate(latestSeenMappingTimestamp()).get,
              treesService.invokePrivate(latestSeenPrefixTimestamp()).get
            )
        )
    ).runToFuture

    whenReady(f) { res =>
      oldLatestSeenMappingTimestamp.get() should be(foundManuallyNewLatestSeenMappingTimestamp.get())
      oldLatestSeenMappingTimestamp.get() should be(res._1)

      oldLatestSeenPrefixTimestamp.get() should be < foundManuallyNewLatestSeenPrefixTimestamp.get()
      oldLatestSeenPrefixTimestamp.get() should be < res._2
      foundManuallyNewLatestSeenPrefixTimestamp.get() should be(res._2)
    }
  }

  it should "reload nothing if neither mappers nor prefixes changed" in {
    val oldLatestSeenMappingTimestamp = new AtomicLong(0L)
    val oldLatestSeenPrefixTimestamp = new AtomicLong(0L)
    val foundManuallyNewLatestSeenMappingTimestamp = new AtomicLong(0L)
    val foundManuallyNewLatestSeenPrefixTimestamp = new AtomicLong(0L)

    val reloadTreesIfNecessaryMethod = PrivateMethod[Task[Unit]](Symbol("reloadTreesIfNecessary"))
    val latestSeenMappingTimestamp = PrivateMethod[Ref[Task, Long]](Symbol("latestSeenMappingTimestamp"))
    val latestSeenPrefixTimestamp = PrivateMethod[Ref[Task, Long]](Symbol("latestSeenPrefixTimestamp"))

    val f = (
      routesService.saveWithOverrides(payload1) >>
        createTreesServiceImpl().flatMap(treesService =>
          treesService.invokePrivate(latestSeenMappingTimestamp()).get.map(v => oldLatestSeenMappingTimestamp.set(v)) >>
            treesService.invokePrivate(latestSeenPrefixTimestamp()).get.map(v => oldLatestSeenPrefixTimestamp.set(v)) >>
            routesService.saveWithOverrides(RoutesResourcePayload(mappers = Option.empty, prefixes = Option.empty)) >>
            mappersAndPrefixesConnection.use { case (mappersOperator, prefixesOperator) =>
              mapperDao
                .findAll(mappersOperator)
                .toListL
                .map(r => foundManuallyNewLatestSeenMappingTimestamp.set(r.map(_.lastUpdateTimestamp).max)) >>
                prefixDao
                  .findAll(prefixesOperator)
                  .toListL
                  .map(r => foundManuallyNewLatestSeenPrefixTimestamp.set(r.map(_.lastUpdateTimestamp).max))
            } >>
            treesService.invokePrivate(reloadTreesIfNecessaryMethod()) >>
            Task.parZip2(
              treesService.invokePrivate(latestSeenMappingTimestamp()).get,
              treesService.invokePrivate(latestSeenPrefixTimestamp()).get
            )
        )
    ).runToFuture

    whenReady(f) { res =>
      oldLatestSeenMappingTimestamp.get() should be(foundManuallyNewLatestSeenMappingTimestamp.get())
      oldLatestSeenMappingTimestamp.get() should be(res._1)

      oldLatestSeenPrefixTimestamp.get() should be(foundManuallyNewLatestSeenPrefixTimestamp.get())
      oldLatestSeenPrefixTimestamp.get() should be(res._2)
    }
  }

  it should "reload nothing if neither mappers nor prefixes changed (RoutesService#save case)" in {
    val oldLatestSeenMappingTimestamp = new AtomicLong(0L)
    val oldLatestSeenPrefixTimestamp = new AtomicLong(0L)
    val foundManuallyNewLatestSeenMappingTimestamp = new AtomicLong(0L)
    val foundManuallyNewLatestSeenPrefixTimestamp = new AtomicLong(0L)

    val reloadTreesIfNecessaryMethod = PrivateMethod[Task[Unit]](Symbol("reloadTreesIfNecessary"))
    val latestSeenMappingTimestamp = PrivateMethod[Ref[Task, Long]](Symbol("latestSeenMappingTimestamp"))
    val latestSeenPrefixTimestamp = PrivateMethod[Ref[Task, Long]](Symbol("latestSeenPrefixTimestamp"))

    val f = (
      routesService.saveWithOverrides(payload1) >>
        createTreesServiceImpl().flatMap(treesService =>
          treesService.invokePrivate(latestSeenMappingTimestamp()).get.map(v => oldLatestSeenMappingTimestamp.set(v)) >>
            treesService.invokePrivate(latestSeenPrefixTimestamp()).get.map(v => oldLatestSeenPrefixTimestamp.set(v)) >>
            routesService.save(payload1) >> // `save` discards duplicated without DB footprint
            mappersAndPrefixesConnection.use { case (mappersOperator, prefixesOperator) =>
              mapperDao
                .findAll(mappersOperator)
                .toListL
                .map(r => foundManuallyNewLatestSeenMappingTimestamp.set(r.map(_.lastUpdateTimestamp).max)) >>
                prefixDao
                  .findAll(prefixesOperator)
                  .toListL
                  .map(r => foundManuallyNewLatestSeenPrefixTimestamp.set(r.map(_.lastUpdateTimestamp).max))
            } >>
            treesService.invokePrivate(reloadTreesIfNecessaryMethod()) >>
            Task.parZip2(
              treesService.invokePrivate(latestSeenMappingTimestamp()).get,
              treesService.invokePrivate(latestSeenPrefixTimestamp()).get
            )
        )
    ).runToFuture

    whenReady(f) { res =>
      oldLatestSeenMappingTimestamp.get() should be(foundManuallyNewLatestSeenMappingTimestamp.get())
      oldLatestSeenMappingTimestamp.get() should be(res._1)

      oldLatestSeenPrefixTimestamp.get() should be(foundManuallyNewLatestSeenPrefixTimestamp.get())
      oldLatestSeenPrefixTimestamp.get() should be(res._2)
    }
  }
}
