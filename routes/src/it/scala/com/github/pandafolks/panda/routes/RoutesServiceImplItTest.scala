package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.payload.{MapperRecordPayload, MappingPayload, RoutesResourcePayload}
import monix.execution.Scheduler
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, EitherValues}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class RoutesServiceImplItTest extends AsyncFlatSpec with RoutesFixture with Matchers with ScalaFutures
  with EitherValues with BeforeAndAfterAll with BeforeAndAfterEach {
  implicit val scheduler: Scheduler = Scheduler.io("routes-service-it-test")

  implicit val defaultConfig: PatienceConfig = PatienceConfig(30.seconds, 100.milliseconds)

  override protected def afterAll(): Unit = mongoContainer.stop()

  override protected def beforeEach(): Unit = Await.result(mappersAndPrefixesConnection.use {
    case (mappers, prefixes) =>
      mappers.db.dropCollection(mappersColName)
      prefixes.db.dropCollection(prefixesColName)
  }.runToFuture, 5.seconds)

  private val payload1 = RoutesResourcePayload(
    mappers = Some(Map.from(List(
      ("/route/one",
        MapperRecordPayload(MappingPayload(
          Right(Map(
            "property1" -> MappingPayload(Left("someEndpoint1")),
            "property2" -> MappingPayload(Right(Map("property22" -> MappingPayload(Left("someEndpoint2"))))),
            "property3" -> MappingPayload(Right(Map(
              "property4" -> MappingPayload(Right(Map("property5" -> MappingPayload(Left("someEndpoint3"))))),
              "property6" -> MappingPayload(Left("someEndpoint4"))
            )))
          )
          )), Some("post"))),
      ("/another/route/{{some_id}}",
        MapperRecordPayload(MappingPayload(Left("group1")))
      ),
      ("groupRoute/**",
        MapperRecordPayload(MappingPayload(Left("group2")))
      )
    ))),
    prefixes = Some(Map.from(List(
      ("group1", "api/v1"),
      ("group2", "api/v2")
    )))
  )

  private val payload2 = RoutesResourcePayload(
    mappers = Some(Map.from(List(
      ("/route/two",
        MapperRecordPayload(MappingPayload(
          Right(Map(
            "property1" -> MappingPayload(Left("someEndpoint1")),
          )
          )), Some("delete"))),
      ("groupRoute/**",
        MapperRecordPayload(MappingPayload(Left("group2"))) // duplicate
      ),
      ("/another/route/{{some_id}}",
        MapperRecordPayload(MappingPayload(Left("group1")), Some("put")) // same but another http method, so there is no duplicate
      ),
    ))),
    prefixes = Some(Map.from(List(
      ("group1", "api/v1"), // duplicate
      ("group3", "api/v3")
    )))
  )

  private val expectedWithoutDuplicates = RoutesResourcePayload(
    mappers = Some(Map.from(List(
      ("/route/one",
        MapperRecordPayload(MappingPayload(
          Right(Map(
            "property1" -> MappingPayload(Left("someEndpoint1")),
            "property2" -> MappingPayload(Right(Map("property22" -> MappingPayload(Left("someEndpoint2"))))),
            "property3" -> MappingPayload(Right(Map(
              "property4" -> MappingPayload(Right(Map("property5" -> MappingPayload(Left("someEndpoint3"))))),
              "property6" -> MappingPayload(Left("someEndpoint4"))
            )))
          )
          )), Some("POST"))),
      ("/another/route/{{some_id}}",
        MapperRecordPayload(MappingPayload(Left("group1")), Some("GET"))
      ),
      ("groupRoute/**",
        MapperRecordPayload(MappingPayload(Left("group2")), Some("GET"))
      ),
      ("/route/two",
        MapperRecordPayload(MappingPayload(
          Right(Map(
            "property1" -> MappingPayload(Left("someEndpoint1")),
          )
          )), Some("DELETE"))),
      ("/another/route/{{some_id}}",
        MapperRecordPayload(MappingPayload(Left("group1")), Some("PUT"))
      ),
    ))),
    prefixes = Some(Map.from(List(
      ("group1", "api/v1"),
      ("group2", "api/v2"),
      ("group3", "api/v3")
    )))
  )

  "RoutesServiceImpl#findAll" should "return all entries saved with RoutesServiceImpl#save" in {
    val f = (routesService.save(payload1)
      >> routesService.save(payload2)
      >> routesService.findAll()
      ).runToFuture

    whenReady(f) { res => res should be(expectedWithoutDuplicates) }
  }

  "RoutesServiceImpl#findAllMappers" should "return all mappers saved with RoutesServiceImpl#save" in {
    val f = (routesService.save(payload1)
      >> routesService.save(payload2)
      >> routesService.findAllMappers()
      ).runToFuture

    whenReady(f) { res => res should be(expectedWithoutDuplicates.mappers.get) }
  }

  "RoutesServiceImpl#findAllPrefixes" should "return all mappers saved with RoutesServiceImpl#save" in {
    val f = (routesService.save(payload1)
      >> routesService.save(payload2)
      >> routesService.findAllPrefixes()
      ).runToFuture

    whenReady(f) { res => res should be(expectedWithoutDuplicates.prefixes.get) }
  }

}
