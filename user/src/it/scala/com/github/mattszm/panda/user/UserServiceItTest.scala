package com.github.mattszm.panda.user

import monix.execution.Scheduler
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID
import scala.concurrent.duration.DurationInt

class UserServiceItTest extends AsyncFlatSpec with UserTokenFixture with Matchers with ScalaFutures with BeforeAndAfterAll {
  implicit val scheduler: Scheduler = Scheduler.io("user-service-it-test")

  implicit val defaultConfig: PatienceConfig = PatienceConfig(30.seconds, 100.milliseconds)

  override protected def afterAll(): Unit = mongoContainer.stop()

  "getById" should "return appropriate user by Id" in {
    val randomName = randomString("name")
    val randomPassword = randomString("password")
    val f = userService.create(randomName, randomPassword)
      .flatMap(id => userService.getById(id.getOrElse(tagUUIDAsUserId(UUID.randomUUID())))).runToFuture

    whenReady(f) { res =>
      res.isDefined should be (true)
      res.get.username should be(randomName)
    }
  }

}
