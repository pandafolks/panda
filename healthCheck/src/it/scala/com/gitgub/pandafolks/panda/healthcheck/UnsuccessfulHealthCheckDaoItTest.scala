package com.gitgub.pandafolks.panda.healthcheck

import cats.implicits.toTraverseOps
import com.github.pandafolks.panda.healthcheck.UnsuccessfulHealthCheck
import com.github.pandafolks.panda.utils.scheduler.CoreScheduler
import monix.eval.Task
import monix.execution.Scheduler
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
  implicit val scheduler: Scheduler = CoreScheduler.scheduler

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

    val clock = java.time.Clock.systemUTC
    val startTimestamp = clock.millis()

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

    val clock = java.time.Clock.systemUTC
    val startTimestamp = clock.millis()

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

  "UnsuccessfulHealthCheckDao#markAsTurnedOff" should "set turnedOff property to true" in {
    val clock = java.time.Clock.systemUTC

    val identifier1 = randomString("1")
    val identifier2 = randomString("2")
    val identifier3 = randomString("3")

    val beforeTimestamp = clock.millis()

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
    val clock = java.time.Clock.systemUTC

    val identifier1 = randomString("1")
    val identifier2 = randomString("2")
    val identifier3 = randomString("3")

    val beforeTimestamp = clock.millis()

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
    val clock = java.time.Clock.systemUTC

    val identifier1 = randomString("1")
    val identifier2 = randomString("2")
    val identifier3 = randomString("3")

    val beforeTimestamp = clock.millis()

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
}
