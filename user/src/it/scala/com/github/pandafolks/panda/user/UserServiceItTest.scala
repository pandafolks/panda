package com.github.pandafolks.panda.user

import monix.eval.Task
import monix.execution.Scheduler
import org.scalatest.{BeforeAndAfterAll, EitherValues}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.implicits.toTraverseOps

import java.util.UUID
import scala.concurrent.duration.DurationInt

class UserServiceItTest extends AsyncFlatSpec with UserTokenFixture with Matchers with ScalaFutures with EitherValues with BeforeAndAfterAll {
  implicit val scheduler: Scheduler = Scheduler.io("user-service-it-test")

  implicit val defaultConfig: PatienceConfig = PatienceConfig(30.seconds, 100.milliseconds)

  override protected def afterAll(): Unit = mongoContainer.stop()

  "create" should "construct and save users" in {
    val randomNames = for (_ <- 0 until 5) yield randomString("username")
    val f = Task.traverse(randomNames)(u => userService.create(u, randomString("password"))).runToFuture

    whenReady(f) { res =>
      res.foreach(r => { r.isRight should be(true) })
      succeed
    }
  }

  "getById" should "find created users" in {
    val randomNames = for (_ <- 0 until 5) yield randomString("username")
    val f = Task.traverse(randomNames)(u => userService.create(u, randomString("password")))
      .flatMap(ids => ids.map(id => userService.getById(id.getOrElse(tagUUIDAsUserId(UUID.randomUUID())))).toList.sequence).runToFuture // kinda hack with the getOrElse

    whenReady(f) { res =>
      randomNames.zip(res).foreach(r => {
        r._2.isDefined should be(true)
        r._1 should be(r._2.get.username)
        r._2.get.password should contain noneOf ("password", r._1)
      })
      succeed
    }
  }

  it should "return empty option if there is no user with requested id" in {
    val randomNames = for (_ <- 0 until 5) yield randomString("username")
    val t = Task.traverse(randomNames)(u => userService.create(u, randomString("password"))) *>
      userService.getById(tagUUIDAsUserId(UUID.randomUUID()))
    val f = t.runToFuture

    whenReady(f) { res => res should be(None) }
  }

  // delete also covers checkPassword method
  "delete" should "remove from db user with delivered credentials" in {
    val randomNames = for (_ <- 0 until 5) yield randomString("username")
    val f1 = Task.traverse(randomNames)(u => userService.create(u, "password")).runToFuture
    val f2 = f1.flatMap(res => userService.delete(UserCredentials(randomNames.head, "password")).runToFuture.map((_, res)))

    whenReady(f2) { res => {
      res._1 should be(true)
      whenReady(res._2.map(
        userId => userService.getById(userId.getOrElse(tagUUIDAsUserId(UUID.randomUUID()))))
        .toList.sequence.runToFuture) { res2 =>
        res2.head should be(None)
        res2.tail.foreach(user => { user.isDefined should be(true) })
        succeed
      }
    }}
  }

  it should "not remove if there is no user with delivered credentials (username mismatch)" in {
    val randomNames = for (_ <- 0 until 5) yield randomString("username")
    val f1 = Task.traverse(randomNames)(u => userService.create(u, "password")).runToFuture
    val f2 = f1.flatMap(res => userService.delete(UserCredentials("radnomUsername", "password")).runToFuture.map((_, res)))

    whenReady(f2) { res => {
      res._1 should be(false)
      whenReady(res._2.map(
        userId => userService.getById(userId.getOrElse(tagUUIDAsUserId(UUID.randomUUID()))))
        .toList.sequence.runToFuture) { res2 =>
        res2.foreach(user => { user.isDefined should be(true) })
        succeed
      }
    }}
  }

  it should "not remove if there is no user with delivered credentials (password mismatch)" in {
    val randomNames = for (_ <- 0 until 5) yield randomString("username")
    val f1 = Task.traverse(randomNames)(u => userService.create(u, "password")).runToFuture
    val f2 = f1.flatMap(res => userService.delete(UserCredentials(randomNames.head, "notMatchingPassword")).runToFuture.map((_, res)))

    whenReady(f2) { res => {
      res._1 should be(false)
      whenReady(res._2.map(
        userId => userService.getById(userId.getOrElse(tagUUIDAsUserId(UUID.randomUUID()))))
        .toList.sequence.runToFuture) { res2 =>
        res2.foreach(user => { user.isDefined should be(true) })
        succeed
      }
    }}
  }
}
