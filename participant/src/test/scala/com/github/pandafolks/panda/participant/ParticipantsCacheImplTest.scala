package com.github.pandafolks.panda.participant

import com.github.pandafolks.panda.participant.event.ParticipantEventService
import com.github.pandafolks.panda.routes.Group
import monix.execution.Scheduler
import monix.execution.Scheduler.global
import org.mockito.Mockito.mock
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers.{be, contain}
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class ParticipantsCacheImplTest extends AsyncFlatSpec {
  implicit final val scheduler: Scheduler = global

  private val mockParticipantEventService = mock(classOf[ParticipantEventService])

  private def createCache(): ParticipantsCacheImpl =
    Await.result(ParticipantsCacheImpl(
      mockParticipantEventService,
      List(
        Participant("59.145.84.51", 4001, Group("cars"), "id1"),
        Participant("59.145.84.52", 4001, Group("cars"), "id2"),
        Participant("59.145.84.53", 4002, Group("planes"), "id3")
      )
    ).runToFuture, 5.seconds)

  "ParticipantsCacheImpl#getAllGroups" should "return all available groups" in {
    val cache: ParticipantsCache = createCache()

    cache.getAllGroups.runToFuture.map(l => l should contain theSameElementsAs List(Group("cars"), Group("planes")))
  }

  "ParticipantsCacheImpl#getParticipantsAssociatedWithGroup" should "return appropriate results for the requested group" in {
    val cache: ParticipantsCache = createCache()

    cache.getParticipantsAssociatedWithGroup(Group("cars")).runToFuture.map(_.toList).map(l => l should
      contain theSameElementsAs List(
      Participant("59.145.84.51", 4001, Group("cars"), "id1"),
      Participant("59.145.84.52", 4001, Group("cars"), "id2")
    ))
  }

  it should "return empty vector if there are no elements associated with the group" in {
    val cache: ParticipantsCache = createCache()
    cache.getParticipantsAssociatedWithGroup(Group("whatever")).runToFuture.map(l => l.size should be(0))
  }

  "ParticipantsCacheImpl#getWorkingParticipantsAssociatedWithGroup" should "return appropriate working participants for the requested group" in {
    val cache = Await.result(ParticipantsCacheImpl(
      mockParticipantEventService,
      List(
        Participant("59.145.84.51", 4001, Group("cars"), "id1", HeartbeatInfo("/heartbeat"), NotWorking),
        Participant("59.145.84.52", 4001, Group("cars"), "id2", HeartbeatInfo("/heartbeat"), Working),
        Participant("59.145.84.53", 4002, Group("planes"), "id3", HeartbeatInfo("/heartbeat"), Working)
      )
    ).runToFuture, 5.seconds)

    cache.getWorkingParticipantsAssociatedWithGroup(Group("cars")).runToFuture.map(_.toList).map(l => l should
      contain theSameElementsAs List(
      Participant("59.145.84.52", 4001, Group("cars"), "id2", HeartbeatInfo("/heartbeat"), Working)
    ))
  }

  it should "return empty vector if there are no working elements associated with the group" in {
    val cache = Await.result(ParticipantsCacheImpl(
      mockParticipantEventService,
      List(
        Participant("59.145.84.51", 4001, Group("cars"), "id1", HeartbeatInfo("/heartbeat"), NotWorking),
        Participant("59.145.84.52", 4001, Group("cars"), "id2", HeartbeatInfo("/heartbeat"), NotWorking),
        Participant("59.145.84.53", 4002, Group("planes"), "id3", HeartbeatInfo("/heartbeat"), Working)
      )
    ).runToFuture, 5.seconds)

    cache.getWorkingParticipantsAssociatedWithGroup(Group("cars")).runToFuture.map(l => l.size should be(0))
  }

  "ParticipantsCacheImpl#getHealthyParticipantsAssociatedWithGroup" should "return appropriate healthy (and working at the same time) participants for the requested group" in {
    val cache = Await.result(ParticipantsCacheImpl(
      mockParticipantEventService,
      List(
        Participant("59.145.84.51", 4001, Group("cars"), "id1", HeartbeatInfo("/heartbeat"), NotWorking, NotHealthy),
        Participant("59.145.84.51", 4002, Group("cars"), "id2", HeartbeatInfo("/heartbeat"), NotWorking, Healthy),
        Participant("59.145.84.51", 4003, Group("cars"), "id3", HeartbeatInfo("/heartbeat"), Working, Healthy),
        Participant("59.145.84.52", 4004, Group("cars"), "id4", HeartbeatInfo("/heartbeat"), Working, NotHealthy),
        Participant("59.145.84.53", 4005, Group("planes"), "id5", HeartbeatInfo("/heartbeat"), Working, NotHealthy),
        Participant("59.145.84.53", 4006, Group("planes"), "id6", HeartbeatInfo("/heartbeat"), Working, Healthy),
        Participant("59.145.84.53", 4007, Group("planes"), "id7", HeartbeatInfo("/heartbeat"), NotWorking, Healthy),
      )
    ).runToFuture, 5.seconds)

    cache.getHealthyParticipantsAssociatedWithGroup(Group("cars")).runToFuture.map(_.toList).map(l => l should
      contain theSameElementsAs List(
      Participant("59.145.84.51", 4003, Group("cars"), "id3", HeartbeatInfo("/heartbeat"), Working, Healthy)
    ))
  }

  it should "return empty vector if there are no healthy elements associated with the group" in {
    val cache = Await.result(ParticipantsCacheImpl(
      mockParticipantEventService,
      List(
        Participant("59.145.84.51", 4001, Group("cars"), "id1", HeartbeatInfo("/heartbeat"), NotWorking, NotHealthy),
        Participant("59.145.84.51", 4002, Group("cars"), "id2", HeartbeatInfo("/heartbeat"), NotWorking, Healthy),
        Participant("59.145.84.52", 4004, Group("cars"), "id4", HeartbeatInfo("/heartbeat"), Working, NotHealthy),
        Participant("59.145.84.53", 4005, Group("planes"), "id5", HeartbeatInfo("/heartbeat"), Working, NotHealthy),
        Participant("59.145.84.53", 4006, Group("planes"), "id6", HeartbeatInfo("/heartbeat"), Working, Healthy),
        Participant("59.145.84.53", 4007, Group("planes"), "id7", HeartbeatInfo("/heartbeat"), NotWorking, Healthy),
      )
    ).runToFuture, 5.seconds)

    cache.getHealthyParticipantsAssociatedWithGroup(Group("cars")).runToFuture.map(l => l.size should be(0))
  }
}
