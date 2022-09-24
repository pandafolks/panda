package com.github.pandafolks.panda.nodestracker

import monix.eval.Task
import org.bson.types.ObjectId
import org.mongodb.scala.model.Filters
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.Clock
import scala.concurrent.duration.DurationInt

class NodeTrackerServiceItTest extends AsyncFlatSpec with NodeTrackerFixture with Matchers with ScalaFutures
  with BeforeAndAfterAll {

  implicit val defaultConfig: PatienceConfig = PatienceConfig(30.seconds, 100.milliseconds)

  override protected def afterAll(): Unit = mongoContainer.stop()

  "getNodeId" should "return randomly generated id once the service is initialized" in {
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

  "getWorkingNodes" should "return all working nodes that notified tracker about itself no earlier than fullConsistencyMaxDelay / 2 seconds ago" in {
    val clock: Clock = java.time.Clock.systemUTC
    val validNode1 = Node(new ObjectId(), clock.millis() - 250)
    val validNode2 = Node(new ObjectId(), clock.millis() - 101)
    val validNode3 = Node(new ObjectId(), clock.millis() - 111)
    val notValidNode1 = Node(new ObjectId(), clock.millis() - 501)
    val notValidNode2 = Node(new ObjectId(), clock.millis() - 999)
    val addedNodes = List(validNode1, validNode2, notValidNode1, notValidNode2, validNode3)

    val f = (nodesConnection.use(c =>
      for {
        _ <- c.single.insertOne(validNode3)
        _ <- c.single.insertOne(validNode1)
        _ <- c.single.insertOne(validNode2)
        _ <- c.single.insertOne(notValidNode1)
        _ <- c.single.insertOne(notValidNode2)
      } yield ()
    ) >>
      nodeTrackerService.getWorkingNodes
      ).runToFuture

    whenReady(f) { res =>
      res.filter(addedNodes.contains(_)) should contain theSameElementsInOrderAs List(validNode1, validNode2, validNode3).sortBy(_._id)
    }
  }

  it should "always return nodes in the same order" in {
    val clock: Clock = java.time.Clock.systemUTC
    //  In this test case we are not checking the filtering property, but sorting, so in order to remove flakiness the creation timestamp of a node is in future
    val validNode1 = Node(new ObjectId(), clock.millis() + 10250)
    val validNode2 = Node(new ObjectId(), clock.millis() + 15101)
    val validNode3 = Node(new ObjectId(), clock.millis() + 15111)
    val notValidNode1 = Node(new ObjectId(), clock.millis() - 501)
    val notValidNode2 = Node(new ObjectId(), clock.millis() - 999)
    val addedNodes = List(validNode1, validNode2, notValidNode1, notValidNode2, validNode3)

    val f = (nodesConnection.use(c =>
      for {
        _ <- c.single.insertOne(validNode3)
        _ <- c.single.insertOne(validNode1)
        _ <- c.single.insertOne(validNode2)
        _ <- c.single.insertOne(notValidNode1)
        _ <- c.single.insertOne(notValidNode2)
      } yield ()
    ) >>
      Task.sequence(List.fill(20)(nodeTrackerService.getWorkingNodes))
      ).runToFuture

    whenReady(f) { res =>
      val filterOut = res.map(_.filter(addedNodes.contains(_)))
      filterOut.forall(_ == filterOut.head) should be(true) // all nested lists are the same
    }
  }

  private def getNode: Task[Node] =
    nodesConnection.use(c => c.source.find(Filters.eq("_id", new ObjectId(nodeTrackerService.getNodeId))).firstL)
}

