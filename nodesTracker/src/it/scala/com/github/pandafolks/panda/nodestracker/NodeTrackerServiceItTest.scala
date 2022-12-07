package com.github.pandafolks.panda.nodestracker

import monix.eval.Task
import org.bson.types.ObjectId
import org.mongodb.scala.model.Filters
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.duration.DurationInt

class NodeTrackerServiceItTest
    extends AsyncFlatSpec
    with NodeTrackerFixture
    with Matchers
    with ScalaFutures
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
      res.map(_._id).forall(_ == res.head._id) should be(true)
      res should contain theSameElementsInOrderAs res.sortBy(_.lastUpdateTimestamp)
    }
  }

  "getWorkingNodes" should "return all working nodes that notified tracker about itself no earlier than fullConsistencyMaxDelay / 2 seconds ago" in {
    val timestamp = System.currentTimeMillis()
    val validNode1 = Node(new ObjectId(), timestamp - 250)
    val validNode2 = Node(new ObjectId(), timestamp - 101)
    val validNode3 = Node(new ObjectId(), timestamp - 111)
    val notValidNode1 = Node(new ObjectId(), timestamp - 501)
    val notValidNode2 = Node(new ObjectId(), timestamp - 999)
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
      nodeTrackerService.getWorkingNodes).runToFuture

    whenReady(f) { res =>
      res.filter(addedNodes.contains(_)) should contain theSameElementsInOrderAs List(
        validNode1,
        validNode2,
        validNode3
      ).sortBy(_._id)
    }
  }

  it should "always return nodes in the same order" in {
    val timestamp = System.currentTimeMillis()
    //  In this test case we are not checking the filtering property, but sorting, so in order to remove flakiness the creation timestamp of a node is in future
    val validNode1 = Node(new ObjectId(), timestamp + 10250)
    val validNode2 = Node(new ObjectId(), timestamp + 15101)
    val validNode3 = Node(new ObjectId(), timestamp + 15111)
    val notValidNode1 = Node(new ObjectId(), timestamp - 501)
    val notValidNode2 = Node(new ObjectId(), timestamp - 999)
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
      Task.sequence(List.fill(20)(nodeTrackerService.getWorkingNodes))).runToFuture

    whenReady(f) { res =>
      val filterOut = res.map(_.filter(addedNodes.contains(_)))
      filterOut.forall(_ == filterOut.head) should be(true) // all nested lists are the same
    }
  }

  "isNodeWorking" should "determinate either node is working" in {
    val validId1 = new ObjectId()
    val validId2 = new ObjectId()
    val validId3 = new ObjectId()
    val notValidId1 = new ObjectId()
    val notValidId2 = new ObjectId()
    val notValidId3 = new ObjectId()

    val timestamp = System.currentTimeMillis()
    val validNode1 = Node(validId1, timestamp + 10250)
    val validNode2 = Node(validId2, timestamp + 15101)
    val validNode3 = Node(validId3, timestamp + 15111)
    val notValidNode1 = Node(notValidId1, timestamp - 501)
    val notValidNode2 = Node(notValidId2, timestamp - 999)

    val f = (
      nodesConnection.use(c =>
        for {
          _ <- c.single.insertMany(List(validNode3, validNode1, notValidNode1, validNode2, notValidNode2))
        } yield ()
      ) >>
        Task.parZip6(
          nodeTrackerService.isNodeWorking(validId1),
          nodeTrackerService.isNodeWorking(notValidId2),
          nodeTrackerService.isNodeWorking(validId3),
          nodeTrackerService.isNodeWorking(notValidId1),
          nodeTrackerService.isNodeWorking(validId2),
          nodeTrackerService.isNodeWorking(notValidId3)
        )
    ).runToFuture

    whenReady(f) { res =>
      res._1 should be(true)
      res._2 should be(false)
      res._3 should be(true)
      res._4 should be(false)
      res._5 should be(true)
      res._6 should be(false)
    }
  }

  "isCurrentNodeResponsibleForJob" should "assign current node and as it is working currently always return true" in {
    val jobName1 = randomString("jobName1")
    val jobName2 = randomString("jobName2")

    val jobs: ConcurrentLinkedQueue[Boolean] = new ConcurrentLinkedQueue[Boolean]()

    val f = (
      nodeTrackerService.isCurrentNodeResponsibleForJob(jobName1).map(r => jobs.add(r)) >>
        nodeTrackerService.isCurrentNodeResponsibleForJob(jobName1).map(r => jobs.add(r)) >>
        nodeTrackerService.isCurrentNodeResponsibleForJob(jobName2).map(r => jobs.add(r)) >>
        nodeTrackerService.isCurrentNodeResponsibleForJob(jobName1).map(r => jobs.add(r)) >>
        nodeTrackerService.isCurrentNodeResponsibleForJob(jobName2).map(r => jobs.add(r)) >>
        Task.sleep(1.seconds) >>
        nodeTrackerService.isCurrentNodeResponsibleForJob(jobName1).map(r => jobs.add(r)) >>
        nodeTrackerService.isCurrentNodeResponsibleForJob(jobName2).map(r => jobs.add(r)) >>
        Task.sleep(3.seconds) >>
        nodeTrackerService.isCurrentNodeResponsibleForJob(jobName1).map(r => jobs.add(r)) >>
        nodeTrackerService.isCurrentNodeResponsibleForJob(jobName2).map(r => jobs.add(r)) >>
        jobsConnection.use(c =>
          for {
            res <- c.source.find(Filters.in(Job.NAME_PROPERTY_NAME, List(jobName1, jobName2): _*)).toListL
          } yield res
        )
    ).runToFuture

    whenReady(f) { res =>
      jobs.forEach(el => {
        el should be(true)
        ()
      })

      res.size should be(2)
      res.head.nodeId should be(res.last.nodeId)
    }
  }

  it should "return false if other working node owns the job" in {
    val jobName = randomString("jobName3")
    val otherNodeId = new ObjectId()

    val f = (
      nodesConnection.use(c =>
        for {
          _ <- c.single.insertOne(Node(otherNodeId, System.currentTimeMillis() + 10250))
        } yield ()
      ) >>
        jobsConnection.use(c =>
          for {
            _ <- c.single.insertOne(Job(jobName, otherNodeId))
          } yield ()
        ) >>
        nodeTrackerService.isCurrentNodeResponsibleForJob(jobName)
    ).runToFuture

    whenReady(f) { res =>
      res should be(false)
    }
  }

  it should "return true if another node owned the job, but the service detected the node may be down and assigned itself to the job" in {
    val jobName = randomString("jobName4")
    val otherNodeId = new ObjectId()

    val f = (
      nodesConnection.use(c =>
        for {
          _ <- c.single.insertOne(Node(otherNodeId, System.currentTimeMillis() - 502))
        } yield ()
      ) >>
        jobsConnection.use(c =>
          for {
            _ <- c.single.insertOne(Job(jobName, otherNodeId))
          } yield ()
        ) >>
        nodeTrackerService.isCurrentNodeResponsibleForJob(jobName)
    ).runToFuture

    whenReady(f) { res =>
      res should be(true)
    }
  }

  private def getNode: Task[Node] =
    nodesConnection.use(c => c.source.find(Filters.eq("_id", new ObjectId(nodeTrackerService.getNodeId))).firstL)
}
