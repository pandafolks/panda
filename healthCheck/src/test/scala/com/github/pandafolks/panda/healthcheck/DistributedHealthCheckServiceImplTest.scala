package com.github.pandafolks.panda.healthcheck

import com.github.pandafolks.panda.backgroundjobsregistry.InMemoryBackgroundJobsRegistryImpl
import com.github.pandafolks.panda.nodestracker.{Node, NodeTrackerService}
import com.github.pandafolks.panda.participant.{Participant, ParticipantsCache}
import com.github.pandafolks.panda.participant.event.ParticipantEventService
import com.github.pandafolks.panda.routes.Group
import com.github.pandafolks.panda.utils.listener.ChangeListener
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import org.bson.types.ObjectId
import org.http4s.client.Client
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, when}
import org.scalacheck.Gen
import org.scalatest.PrivateMethodTester
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers.be
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

import scala.util.hashing.MurmurHash3

class DistributedHealthCheckServiceImplTest extends AsyncFlatSpec with ScalaFutures with PrivateMethodTester {
  implicit val scheduler: SchedulerService =
    Scheduler.forkJoin(Runtime.getRuntime.availableProcessors() * 2, Runtime.getRuntime.availableProcessors() * 2)

  "DistributedHealthCheckServiceImpl#getNodesSizeWithCurrentNodePosition" should "return double empty if there are no working nodes" in {
    val mockNodeTrackerService = mock(classOf[NodeTrackerService])
    when(mockNodeTrackerService.getWorkingNodes) thenReturn Task.now(List.empty)
    when(mockNodeTrackerService.getNodeId) thenReturn "randomId"

    val participantsCacheMock = mock(classOf[ParticipantsCache])
    when(participantsCacheMock.registerListener(any[ChangeListener[Participant]]())) thenReturn Task.unit

    val distributedHealthCheckServiceImpl = new DistributedHealthCheckServiceImpl(
      mock(classOf[ParticipantEventService]),
      participantsCacheMock,
      mockNodeTrackerService,
      mock(classOf[UnsuccessfulHealthCheckDao]),
      mock(classOf[Client[Task]]),
      new InMemoryBackgroundJobsRegistryImpl(scheduler)
    )(HealthCheckConfig(-1, -1, Option.empty, Option.empty, Option.empty))(scheduler)
    val underTestMethod = PrivateMethod[Task[(Option[Int], Option[Int])]](Symbol("getNodesSizeWithCurrentNodePosition"))
    val f = distributedHealthCheckServiceImpl.invokePrivate(underTestMethod()).runToFuture

    whenReady(f) { res =>
      res should be((Option.empty, Option.empty))
    }
  }

  it should "return double empty if the node list does not contain current node" in {
    val mockNodeTrackerService = mock(classOf[NodeTrackerService])
    when(mockNodeTrackerService.getWorkingNodes) thenReturn Task.now(List.fill(5)(Node(new ObjectId(), 1232134)))
    when(mockNodeTrackerService.getNodeId) thenReturn "randomId"

    val participantsCacheMock = mock(classOf[ParticipantsCache])
    when(participantsCacheMock.registerListener(any[ChangeListener[Participant]]())) thenReturn Task.unit

    val distributedHealthCheckServiceImpl = new DistributedHealthCheckServiceImpl(
      mock(classOf[ParticipantEventService]),
      participantsCacheMock,
      mockNodeTrackerService,
      mock(classOf[UnsuccessfulHealthCheckDao]),
      mock(classOf[Client[Task]]),
      new InMemoryBackgroundJobsRegistryImpl(scheduler)
    )(HealthCheckConfig(-1, -1, Option.empty, Option.empty, Option.empty))(scheduler)
    val underTestMethod = PrivateMethod[Task[(Option[Int], Option[Int])]](Symbol("getNodesSizeWithCurrentNodePosition"))
    val f = distributedHealthCheckServiceImpl.invokePrivate(underTestMethod()).runToFuture

    whenReady(f) { res =>
      res should be((Option.empty, Option.empty))
    }
  }

  it should "return tuple with size and position of the current node if the one found" in {
    val mockNodeTrackerService = mock(classOf[NodeTrackerService])
    val allNodes = List.fill(10)(Node(new ObjectId(), 1232134))
    when(mockNodeTrackerService.getWorkingNodes) thenReturn Task.now(allNodes)
    when(mockNodeTrackerService.getNodeId) thenReturn allNodes(3)._id.toHexString

    val participantsCacheMock = mock(classOf[ParticipantsCache])
    when(participantsCacheMock.registerListener(any[ChangeListener[Participant]]())) thenReturn Task.unit

    val distributedHealthCheckServiceImpl = new DistributedHealthCheckServiceImpl(
      mock(classOf[ParticipantEventService]),
      participantsCacheMock,
      mockNodeTrackerService,
      mock(classOf[UnsuccessfulHealthCheckDao]),
      mock(classOf[Client[Task]]),
      new InMemoryBackgroundJobsRegistryImpl(scheduler)
    )(HealthCheckConfig(-1, -1, Option.empty, Option.empty, Option.empty))(scheduler)
    val underTestMethod = PrivateMethod[Task[(Option[Int], Option[Int])]](Symbol("getNodesSizeWithCurrentNodePosition"))
    val f = distributedHealthCheckServiceImpl.invokePrivate(underTestMethod()).runToFuture

    whenReady(f) { res =>
      res should be((Some(10), Some(3)))
    }
  }

  "DistributedHealthCheckServiceImpl#pickParticipantsForNode" should "pick appropriate participants based on delivered position and this choice must be repeatable" in {
    val participants = List.fill(20)(Participant("randomHost", -1, Group("randomGroup"), randomString("identifier")))

    val participantsCacheMock = mock(classOf[ParticipantsCache])
    when(participantsCacheMock.registerListener(any[ChangeListener[Participant]]())) thenReturn Task.unit

    val distributedHealthCheckServiceImpl = new DistributedHealthCheckServiceImpl(
      mock(classOf[ParticipantEventService]),
      participantsCacheMock,
      mock(classOf[NodeTrackerService]),
      mock(classOf[UnsuccessfulHealthCheckDao]),
      mock(classOf[Client[Task]]),
      new InMemoryBackgroundJobsRegistryImpl(scheduler)
    )(HealthCheckConfig(-1, -1, Option.empty, Option.empty, Option.empty))(scheduler)
    val underTestMethod = PrivateMethod[List[Participant]](Symbol("pickParticipantsForNode"))

    val result =
      for (_ <- 0 until 10)
        yield distributedHealthCheckServiceImpl.invokePrivate(
          underTestMethod(participants, 5, 2)
        ) // index 2 means third node out of 5

    result.forall(chosenParticipants => chosenParticipants.forall(p => Math.abs(MurmurHash3.stringHash(p.identifier)) % 5 == 2)) should be(
      true
    )
    result.forall(_ == result.head) should be(true)
  }

  it should "return nothing if delivered position is out of scope" in {
    val participants =
      List.fill(20)(Participant("randomHost", -1, Group("randomGroup"), randomString("anotherIdentifier")))

    val participantsCacheMock = mock(classOf[ParticipantsCache])
    when(participantsCacheMock.registerListener(any[ChangeListener[Participant]]())) thenReturn Task.unit

    val distributedHealthCheckServiceImpl = new DistributedHealthCheckServiceImpl(
      mock(classOf[ParticipantEventService]),
      participantsCacheMock,
      mock(classOf[NodeTrackerService]),
      mock(classOf[UnsuccessfulHealthCheckDao]),
      mock(classOf[Client[Task]]),
      new InMemoryBackgroundJobsRegistryImpl(scheduler)
    )(HealthCheckConfig(-1, -1, Option.empty, Option.empty, Option.empty))(scheduler)
    val underTestMethod = PrivateMethod[List[Participant]](Symbol("pickParticipantsForNode"))

    val result =
      for (_ <- 0 until 10)
        yield distributedHealthCheckServiceImpl.invokePrivate(underTestMethod(participants, 5, 5)) // out of scope

    result.forall(_.isEmpty) should be(true)
  }

  private def randomString(prefix: String): String = Gen.uuid.map(prefix + _.toString.take(20)).sample.get
}
