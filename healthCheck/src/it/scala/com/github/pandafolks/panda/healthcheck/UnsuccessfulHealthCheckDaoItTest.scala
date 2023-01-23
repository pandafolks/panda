package com.github.pandafolks.panda.healthcheck

import cats.implicits.toTraverseOps
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class UnsuccessfulHealthCheckDaoItTest
    extends AsyncFlatSpec
    with UnsuccessfulHealthCheckFixture
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll
    with BeforeAndAfterEach {
  implicit val scheduler: SchedulerService =
    Scheduler.forkJoin(Runtime.getRuntime.availableProcessors() * 2, Runtime.getRuntime.availableProcessors() * 2)

  implicit val defaultConfig: PatienceConfig = PatienceConfig(30.seconds, 100.milliseconds)

  override protected def afterAll(): Unit = mongoContainer.stop()

  override protected def beforeEach(): Unit = Await.result(
    unsuccessfulHealthCheckConnection.use(c => c.db.dropCollection(unsuccessfulHealthCheckColName)).runToFuture,
    5.seconds
  )

  "UnsuccessfulHealthCheckDao#incrementCounter" should "create an entity if does not exist and return incremented value" in {
    val identifier = randomString("one")

    val f = List.fill(4)(unsuccessfulHealthCheckDao.incrementCounter(identifier)).sequence.runToFuture

    whenReady(f) { res =>
      res should contain theSameElementsInOrderAs (for (i <- 1 to 4) yield Right(i)).toList
    }
  }

  it should "be able to handle multiple identifiers and upgrade their counters independently" in {
    val identifier1 = randomString("1")
    val identifier2 = randomString("2")
    val identifier3 = randomString("3")
    val identifier4 = randomString("4")

    val f = (
      unsuccessfulHealthCheckDao.incrementCounter(identifier1) >>
        Task.parZip4(
          Task.sequence(List.fill(50)(unsuccessfulHealthCheckDao.incrementCounter(identifier1))),
          Task.sequence(List.fill(50)(unsuccessfulHealthCheckDao.incrementCounter(identifier2))),
          Task.sequence(List.fill(50)(unsuccessfulHealthCheckDao.incrementCounter(identifier3))),
          Task.sequence(List.fill(50)(unsuccessfulHealthCheckDao.incrementCounter(identifier4)))
        )
    ).runToFuture

    whenReady(f) { res =>
      res._1 should contain theSameElementsInOrderAs (for (i <- 2 to 51) yield Right(i)).toList
      res._2 should contain theSameElementsInOrderAs (for (i <- 1 to 50) yield Right(i)).toList
      res._3 should contain theSameElementsInOrderAs (for (i <- 1 to 50) yield Right(i)).toList
      res._4 should contain theSameElementsInOrderAs (for (i <- 1 to 50) yield Right(i)).toList
    }
  }

  "UnsuccessfulHealthCheckDao#clear" should "remove an entry corresponding to the requested identifier" in {
    val identifier1 = randomString("w1")
    val identifier2 = randomString("w2")

    val startTimestamp = System.currentTimeMillis()

    val f = (
      unsuccessfulHealthCheckDao.incrementCounter(identifier1)
        >> unsuccessfulHealthCheckDao.incrementCounter(identifier2)
        >> unsuccessfulHealthCheckDao.clear(identifier1)
        >> unsuccessfulHealthCheckConnection.use(c => c.source.findAll.toListL)
    ).runToFuture

    whenReady(f) { res =>
      res.size should be(1)
      res.head.identifier should be(identifier2)
      res.head.counter should be(1)
      res.head.lastUpdateTimestamp should be > startTimestamp
    }
  }

  it should "finish successfully if there was no items to delete" in {
    val identifier1 = randomString("c1")
    val identifier2 = randomString("c2")
    val identifier3 = randomString("c3")

    val startTimestamp = System.currentTimeMillis()

    val f = (
      unsuccessfulHealthCheckDao.incrementCounter(identifier1)
        >> unsuccessfulHealthCheckDao.incrementCounter(identifier2)
        >> unsuccessfulHealthCheckDao.clear(identifier3)
        >> unsuccessfulHealthCheckConnection.use(c => c.source.findAll.toListL)
    ).runToFuture

    whenReady(f) { res =>
      res.size should be(2)

      List(identifier1, identifier2) should contain(res.head.identifier)
      res.head.counter should be(1)
      List(identifier1, identifier2) should contain(res(1).identifier)
      res(1).counter should be(1)

      res.head.identifier should not be res(1).identifier

      res.head.lastUpdateTimestamp should be > startTimestamp
      res(1).lastUpdateTimestamp should be > startTimestamp
    }
  }

  "UnsuccessfulHealthCheckDao#clear(many identifiers)" should "remove entries corresponding to the requested identifiers" in {
    val identifier1 = randomString("w3")
    val identifier2 = randomString("w4")
    val identifier3 = randomString("w5")
    val identifierNotExisting = randomString("w6")

    val f = (
      unsuccessfulHealthCheckDao.incrementCounter(identifier1)
        >> unsuccessfulHealthCheckDao.incrementCounter(identifier2)
        >> unsuccessfulHealthCheckDao.incrementCounter(identifier3)
        >> unsuccessfulHealthCheckDao.clear(List(identifier1, identifier2, identifierNotExisting))
        >> unsuccessfulHealthCheckConnection.use(c => c.source.findAll.toListL)
    ).runToFuture

    whenReady(f) { res =>
      res.size should be(1)
      res.head.identifier should be(identifier3)
      res.head.counter should be(1)
    }
  }

  it should "not remove anything when the input list is empty" in {
    val identifier1 = randomString("w7")
    val identifier2 = randomString("w8")
    val identifier3 = randomString("w9")

    val f = (
      unsuccessfulHealthCheckDao.incrementCounter(identifier1)
        >> unsuccessfulHealthCheckDao.incrementCounter(identifier2)
        >> unsuccessfulHealthCheckDao.incrementCounter(identifier3)
        >> unsuccessfulHealthCheckDao.clear(List.empty)
        >> unsuccessfulHealthCheckConnection.use(c => c.source.findAll.toListL)
    ).runToFuture

    whenReady(f) { res =>
      res.size should be(3)
    }
  }

  "UnsuccessfulHealthCheckDao#markAsTurnedOff" should "set turnedOff property to true" in {

    val identifier1 = randomString("1")
    val identifier2 = randomString("2")
    val identifier3 = randomString("3")

    val beforeTimestamp = System.currentTimeMillis()

    val f = (
      unsuccessfulHealthCheckDao.incrementCounter(identifier1) >>
        unsuccessfulHealthCheckDao.incrementCounter(identifier2) >>
        unsuccessfulHealthCheckDao.incrementCounter(identifier3) >>
        unsuccessfulHealthCheckDao.markAsTurnedOff(List(identifier1, identifier3)) >>
        unsuccessfulHealthCheckDao.incrementCounter(identifier3) >>
        unsuccessfulHealthCheckConnection.use(c => c.source.findAll.toListL)
    ).runToFuture

    whenReady(f) { res =>
      val m = res.foldLeft(Map.empty[String, UnsuccessfulHealthCheck])((prev, el) => prev + (el.identifier -> el))

      val ide1 = m(identifier1)
      ide1.counter should be(1L)
      ide1.lastUpdateTimestamp should be > beforeTimestamp
      ide1.turnedOff should be(true)

      val ide2 = m(identifier2)
      ide2.counter should be(1L)
      ide2.lastUpdateTimestamp should be > beforeTimestamp
      ide2.turnedOff should be(false)

      val ide3 = m(identifier3)
      ide3.counter should be(2L)
      ide3.lastUpdateTimestamp should be > beforeTimestamp
      ide3.turnedOff should be(true)
    }
  }

  it should "handle case when there are multiple same identifiers" in {
    val identifier1 = randomString("1")
    val identifier2 = randomString("2")
    val identifier3 = randomString("3")

    val beforeTimestamp = System.currentTimeMillis()

    val f = (
      unsuccessfulHealthCheckDao.incrementCounter(identifier1) >>
        unsuccessfulHealthCheckDao.incrementCounter(identifier2) >>
        unsuccessfulHealthCheckDao.incrementCounter(identifier3) >>
        unsuccessfulHealthCheckDao.markAsTurnedOff(List(identifier1, identifier3, identifier1, identifier1)) >>
        unsuccessfulHealthCheckDao.incrementCounter(identifier3) >>
        unsuccessfulHealthCheckConnection.use(c => c.source.findAll.toListL)
    ).runToFuture

    whenReady(f) { res =>
      val m = res.foldLeft(Map.empty[String, UnsuccessfulHealthCheck])((prev, el) => prev + (el.identifier -> el))

      val ide1 = m(identifier1)
      ide1.counter should be(1L)
      ide1.lastUpdateTimestamp should be > beforeTimestamp
      ide1.turnedOff should be(true)

      val ide2 = m(identifier2)
      ide2.counter should be(1L)
      ide2.lastUpdateTimestamp should be > beforeTimestamp
      ide2.turnedOff should be(false)

      val ide3 = m(identifier3)
      ide3.counter should be(2L)
      ide3.lastUpdateTimestamp should be > beforeTimestamp
      ide3.turnedOff should be(true)
    }
  }

  it should "handle case when the item with requested identifier does not exist" in {
    val identifier1 = randomString("1")
    val identifier2 = randomString("2")
    val identifier3 = randomString("3")

    val beforeTimestamp = System.currentTimeMillis()

    val f = (
      unsuccessfulHealthCheckDao.incrementCounter(identifier2) >>
        unsuccessfulHealthCheckDao.incrementCounter(identifier3) >>
        unsuccessfulHealthCheckDao.markAsTurnedOff(List(identifier1, identifier3, identifier1, identifier1)) >>
        unsuccessfulHealthCheckDao.incrementCounter(identifier3) >>
        unsuccessfulHealthCheckConnection.use(c => c.source.findAll.toListL)
    ).runToFuture

    whenReady(f) { res =>
      val m = res.foldLeft(Map.empty[String, UnsuccessfulHealthCheck])((prev, el) => prev + (el.identifier -> el))

      m.size should be(2)

      val ide2 = m(identifier2)
      ide2.counter should be(1L)
      ide2.lastUpdateTimestamp should be > beforeTimestamp
      ide2.turnedOff should be(false)

      val ide3 = m(identifier3)
      ide3.counter should be(2L)
      ide3.lastUpdateTimestamp should be > beforeTimestamp
      ide3.turnedOff should be(true)
    }
  }

  "UnsuccessfulHealthCheckDao#getStaleEntries" should "return results according to applied arguments" in {
    val identifier1 = randomString("getStaleEntries1")
    val identifier2 = randomString("getStaleEntries2")
    val identifier3 = randomString("getStaleEntries3")
    val identifier4 = randomString("getStaleEntries4")

    val beforeTimestamp = System.currentTimeMillis()

    val f = (unsuccessfulHealthCheckConnection.use { op =>
      op.single.insertMany(
        Seq(
          UnsuccessfulHealthCheck(
            identifier1,
            4,
            beforeTimestamp - 1000L,
            turnedOff = false
          ), // 1 second old - invalid because the timestamp is too fresh
          UnsuccessfulHealthCheck(
            identifier2,
            4,
            beforeTimestamp - 6000L,
            turnedOff = false
          ), // 5 second old - valid because the entry id old enough and the counter is 4
          UnsuccessfulHealthCheck(
            identifier3,
            3,
            beforeTimestamp,
            turnedOff = false
          ), // invalid because counter is too small
          UnsuccessfulHealthCheck(
            identifier4,
            5,
            beforeTimestamp - 5000L,
            turnedOff = false
          ) // valid because the entry id old enough and the counter is 5
        )
      )
    } >>
      unsuccessfulHealthCheckDao.getStaleEntries(4000L, 4)).runToFuture

    whenReady(f) { res =>
      res.size should be(2)
      res.map(_.identifier) should contain theSameElementsAs List(identifier2, identifier4)
    }
  }

  it should "return empty list if no results for applied arguments exists" in {
    val identifier1 = randomString("getStaleEntries5")
    val identifier2 = randomString("getStaleEntries6")
    val identifier3 = randomString("getStaleEntries7")

    val beforeTimestamp = System.currentTimeMillis()
    val f = (
      unsuccessfulHealthCheckConnection.use { op =>
        op.single.insertMany(
          Seq(
            UnsuccessfulHealthCheck(identifier1, 76, beforeTimestamp - 1000L, turnedOff = false),
            UnsuccessfulHealthCheck(identifier2, 4, beforeTimestamp - 5000L, turnedOff = false),
            UnsuccessfulHealthCheck(identifier3, 3, beforeTimestamp - 10000L, turnedOff = false)
          )
        )
      } >>
        unsuccessfulHealthCheckDao.getStaleEntries(6010, 4)
    ).runToFuture

    whenReady(f) { res =>
      res.size should be(0)
    }
  }
}
