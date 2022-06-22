package com.gitgub.pandafolks.panda.healthcheck

import cats.implicits.toTraverseOps
import com.github.pandafolks.panda.healthcheck.UnsuccessfulHealthCheck
import monix.eval.Task
import monix.execution.Scheduler
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class UnsuccessfulHealthCheckDaoItTest extends AsyncFlatSpec with UnsuccessfulHealthCheckFixture with Matchers with ScalaFutures
  with BeforeAndAfterAll with BeforeAndAfterEach {
  implicit val scheduler: Scheduler = Scheduler.io("node-tracker-service-it-test")

  implicit val defaultConfig: PatienceConfig = PatienceConfig(30.seconds, 100.milliseconds)

  override protected def afterAll(): Unit = mongoContainer.stop()

  override protected def beforeEach(): Unit = Await.result(unsuccessfulHealthCheckConnection.use(c =>
    c.db.dropCollection(unsuccessfulHealthCheckColName)).runToFuture, 5.seconds)

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
          Task.sequence(List.fill(50)(unsuccessfulHealthCheckDao.incrementCounter(identifier4))),
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

    val f = (
      unsuccessfulHealthCheckDao.incrementCounter(identifier1)
        >> unsuccessfulHealthCheckDao.incrementCounter(identifier2)
        >> unsuccessfulHealthCheckDao.clear(identifier1)
        >> unsuccessfulHealthCheckConnection.use(c => c.source.findAll.toListL)
      ).runToFuture

    whenReady(f) { res =>
      res.size should be (1)
      res should contain theSameElementsAs List(UnsuccessfulHealthCheck(identifier2, 1))
    }
  }

  it should "finish successfully if there was no items to delete" in {
    val identifier1 = randomString("c1")
    val identifier2 = randomString("c2")
    val identifier3 = randomString("c3")

    val f = (
      unsuccessfulHealthCheckDao.incrementCounter(identifier1)
        >> unsuccessfulHealthCheckDao.incrementCounter(identifier2)
        >> unsuccessfulHealthCheckDao.clear(identifier3)
        >> unsuccessfulHealthCheckConnection.use(c => c.source.findAll.toListL)
      ).runToFuture

    whenReady(f) { res =>
      res.size should be (2)
      res should contain theSameElementsAs  List(UnsuccessfulHealthCheck(identifier2, 1), UnsuccessfulHealthCheck(identifier1, 1))
    }
  }
}
