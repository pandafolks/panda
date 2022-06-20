package com.github.pandafolks.panda.nodestracker

import monix.eval.Task
import monix.execution.Scheduler
import org.bson.types.ObjectId
import org.mongodb.scala.model.Filters
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.DurationInt

class NodeTrackerServiceItTest extends AsyncFlatSpec with NodeTrackerFixture with Matchers with ScalaFutures
  with BeforeAndAfterAll {
  implicit val scheduler: Scheduler = Scheduler.io("node-tracker-service-it-test")

  implicit val defaultConfig: PatienceConfig = PatienceConfig(30.seconds, 100.milliseconds)

  override protected def afterAll(): Unit = mongoContainer.stop()

  "nodeId" should "return randomly generated id once the service is initialized" in {
    ObjectId.isValid(nodeTrackerService.getNodeId) should be(true)
  }

  "background job" should "update timestamp every fullConsistencyMaxDelay / 4 seconds" in {
    // We are sleeping fullConsistencyMaxDelay / 2 instead of / 4 in order to reduce flakiness.
    val f = (
      for {
        firstTimeStamp <- getNode
        _ <- Task.sleep(500.millisecond)
        secondsTimeStamp <- getNode
        _ <- Task.sleep(500.millisecond)
        thirdTimeStamp <- getNode
      } yield List(firstTimeStamp, secondsTimeStamp, thirdTimeStamp)
      ).runToFuture

    whenReady(f) { res =>
      res.map(_._id).forall(_ == res.head._id) should be (true)
      res should contain theSameElementsInOrderAs res.sortBy(_.lastUpdateTimestamp)
    }
  }

  private def getNode: Task[Node] =
    nodesConnection.use(c => c.source.find(Filters.eq("_id", new ObjectId(nodeTrackerService.getNodeId))).firstL)
}

