package com.github.pandafolks.panda.participant

import com.github.pandafolks.panda.backgroundjobsregistry.InMemoryBackgroundJobsRegistryImpl
import com.github.pandafolks.panda.participant.event.ParticipantEventService
import com.github.pandafolks.panda.routes.Group
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import org.mockito.Mockito.mock
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers.{be, contain}
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class ParticipantsCacheImplTest extends AsyncFlatSpec {
  implicit val scheduler: SchedulerService = Scheduler.forkJoin(Runtime.getRuntime.availableProcessors() * 2, Runtime.getRuntime.availableProcessors() * 2)

  private val mockParticipantEventService = mock(classOf[ParticipantEventService])

  private def createCache(): ParticipantsCacheImpl =
    Await.result(
      ParticipantsCacheImpl(
        mockParticipantEventService,
        new InMemoryBackgroundJobsRegistryImpl(scheduler),
        List(
          Participant("59.145.84.51", 4001, Group("cars"), "id1"),
          Participant("59.145.84.52", 4001, Group("cars"), "id2"),
          Participant("59.145.84.53", 4002, Group("planes"), "id3")
        )
      ).runToFuture,
      5.seconds
    )

  "ParticipantsCacheImpl#getAllGroups" should "return all available groups" in {
    val cache: ParticipantsCache = createCache()

    cache.getAllGroups.runToFuture.map(l => l should contain theSameElementsAs List(Group("cars"), Group("planes")))
  }

  "ParticipantsCacheImpl#getAllParticipants" should "return all participants" in {
    val cache: ParticipantsCache = createCache()

    cache.getAllParticipants.runToFuture
      .map(_.toList)
      .map(l =>
        l should
          contain theSameElementsAs List(
            Participant("59.145.84.51", 4001, Group("cars"), "id1"),
            Participant("59.145.84.52", 4001, Group("cars"), "id2"),
            Participant("59.145.84.53", 4002, Group("planes"), "id3")
          )
      )
  }

  "ParticipantsCacheImpl#getAllWorkingParticipants" should "return all working participants" in {
    val cache = Await.result(
      ParticipantsCacheImpl(
        mockParticipantEventService,
        new InMemoryBackgroundJobsRegistryImpl(scheduler),
        List(
          Participant("59.145.84.51", 4001, Group("cars"), "id1", HealthcheckInfo("/heartbeat"), NotWorking),
          Participant("59.145.84.52", 4001, Group("cars"), "id2", HealthcheckInfo("/heartbeat"), Working),
          Participant("59.145.84.53", 4002, Group("planes"), "id3", HealthcheckInfo("/heartbeat"), Working)
        )
      ).runToFuture,
      5.seconds
    )

    cache.getAllWorkingParticipants.runToFuture
      .map(_.toList)
      .map(l =>
        l should
          contain theSameElementsAs List(
            Participant("59.145.84.52", 4001, Group("cars"), "id2", HealthcheckInfo("/heartbeat"), Working),
            Participant("59.145.84.53", 4002, Group("planes"), "id3", HealthcheckInfo("/heartbeat"), Working)
          )
      )
  }

  "ParticipantsCacheImpl#getAllHealthyParticipants" should "return all working participants" in {
    val cache = Await.result(
      ParticipantsCacheImpl(
        mockParticipantEventService,
        new InMemoryBackgroundJobsRegistryImpl(scheduler),
        List(
          Participant("59.145.84.51", 4001, Group("cars"), "id1", HealthcheckInfo("/heartbeat"), NotWorking, Unhealthy),
          Participant("59.145.84.51", 4002, Group("cars"), "id2", HealthcheckInfo("/heartbeat"), NotWorking, Healthy),
          Participant("59.145.84.51", 4003, Group("cars"), "id3", HealthcheckInfo("/heartbeat"), Working, Healthy),
          Participant("59.145.84.52", 4004, Group("cars"), "id4", HealthcheckInfo("/heartbeat"), Working, Unhealthy),
          Participant("59.145.84.53", 4005, Group("planes"), "id5", HealthcheckInfo("/heartbeat"), Working, Unhealthy),
          Participant("59.145.84.53", 4006, Group("planes"), "id6", HealthcheckInfo("/heartbeat"), Working, Healthy),
          Participant("59.145.84.53", 4007, Group("planes"), "id7", HealthcheckInfo("/heartbeat"), NotWorking, Healthy)
        )
      ).runToFuture,
      5.seconds
    )

    cache.getAllHealthyParticipants.runToFuture
      .map(_.toList)
      .map(l =>
        l should
          contain theSameElementsAs List(
            Participant("59.145.84.51", 4003, Group("cars"), "id3", HealthcheckInfo("/heartbeat"), Working, Healthy),
            Participant("59.145.84.53", 4006, Group("planes"), "id6", HealthcheckInfo("/heartbeat"), Working, Healthy)
          )
      )
  }

  "ParticipantsCacheImpl#getParticipantsAssociatedWithGroup" should "return appropriate results for the requested group" in {
    val cache: ParticipantsCache = createCache()

    cache
      .getParticipantsAssociatedWithGroup(Group("cars"))
      .runToFuture
      .map(_.toList)
      .map(l =>
        l should
          contain theSameElementsAs List(
            Participant("59.145.84.51", 4001, Group("cars"), "id1"),
            Participant("59.145.84.52", 4001, Group("cars"), "id2")
          )
      )
  }

  it should "return empty vector if there are no elements associated with the group" in {
    val cache: ParticipantsCache = createCache()
    cache.getParticipantsAssociatedWithGroup(Group("whatever")).runToFuture.map(l => l.size should be(0))
  }

  "ParticipantsCacheImpl#getWorkingParticipantsAssociatedWithGroup" should "return appropriate working participants for the requested group" in {
    val cache = Await.result(
      ParticipantsCacheImpl(
        mockParticipantEventService,
        new InMemoryBackgroundJobsRegistryImpl(scheduler),
        List(
          Participant("59.145.84.51", 4001, Group("cars"), "id1", HealthcheckInfo("/heartbeat"), NotWorking),
          Participant("59.145.84.52", 4001, Group("cars"), "id2", HealthcheckInfo("/heartbeat"), Working),
          Participant("59.145.84.53", 4002, Group("planes"), "id3", HealthcheckInfo("/heartbeat"), Working)
        )
      ).runToFuture,
      5.seconds
    )

    cache
      .getWorkingParticipantsAssociatedWithGroup(Group("cars"))
      .runToFuture
      .map(_.toList)
      .map(l =>
        l should
          contain theSameElementsAs List(
            Participant("59.145.84.52", 4001, Group("cars"), "id2", HealthcheckInfo("/heartbeat"), Working)
          )
      )
  }

  it should "return empty vector if there are no working elements associated with the group" in {
    val cache = Await.result(
      ParticipantsCacheImpl(
        mockParticipantEventService,
        new InMemoryBackgroundJobsRegistryImpl(scheduler),
        List(
          Participant("59.145.84.51", 4001, Group("cars"), "id1", HealthcheckInfo("/heartbeat"), NotWorking),
          Participant("59.145.84.52", 4001, Group("cars"), "id2", HealthcheckInfo("/heartbeat"), NotWorking),
          Participant("59.145.84.53", 4002, Group("planes"), "id3", HealthcheckInfo("/heartbeat"), Working)
        )
      ).runToFuture,
      5.seconds
    )

    cache.getWorkingParticipantsAssociatedWithGroup(Group("cars")).runToFuture.map(l => l.size should be(0))
  }

  "ParticipantsCacheImpl#getHealthyParticipantsAssociatedWithGroup" should "return appropriate healthy (and working at the same time) participants for the requested group" in {
    val cache = Await.result(
      ParticipantsCacheImpl(
        mockParticipantEventService,
        new InMemoryBackgroundJobsRegistryImpl(scheduler),
        List(
          Participant("59.145.84.51", 4001, Group("cars"), "id1", HealthcheckInfo("/heartbeat"), NotWorking, Unhealthy),
          Participant("59.145.84.51", 4002, Group("cars"), "id2", HealthcheckInfo("/heartbeat"), NotWorking, Healthy),
          Participant("59.145.84.51", 4003, Group("cars"), "id3", HealthcheckInfo("/heartbeat"), Working, Healthy),
          Participant("59.145.84.52", 4004, Group("cars"), "id4", HealthcheckInfo("/heartbeat"), Working, Unhealthy),
          Participant("59.145.84.53", 4005, Group("planes"), "id5", HealthcheckInfo("/heartbeat"), Working, Unhealthy),
          Participant("59.145.84.53", 4006, Group("planes"), "id6", HealthcheckInfo("/heartbeat"), Working, Healthy),
          Participant("59.145.84.53", 4007, Group("planes"), "id7", HealthcheckInfo("/heartbeat"), NotWorking, Healthy)
        )
      ).runToFuture,
      5.seconds
    )

    cache
      .getHealthyParticipantsAssociatedWithGroup(Group("cars"))
      .runToFuture
      .map(_.toList)
      .map(l =>
        l should
          contain theSameElementsAs List(
            Participant("59.145.84.51", 4003, Group("cars"), "id3", HealthcheckInfo("/heartbeat"), Working, Healthy)
          )
      )
  }

  it should "return empty vector if there are no healthy elements associated with the group" in {
    val cache = Await.result(
      ParticipantsCacheImpl(
        mockParticipantEventService,
        new InMemoryBackgroundJobsRegistryImpl(scheduler),
        List(
          Participant("59.145.84.51", 4001, Group("cars"), "id1", HealthcheckInfo("/heartbeat"), NotWorking, Unhealthy),
          Participant("59.145.84.51", 4002, Group("cars"), "id2", HealthcheckInfo("/heartbeat"), NotWorking, Healthy),
          Participant("59.145.84.52", 4004, Group("cars"), "id4", HealthcheckInfo("/heartbeat"), Working, Unhealthy),
          Participant("59.145.84.53", 4005, Group("planes"), "id5", HealthcheckInfo("/heartbeat"), Working, Unhealthy),
          Participant("59.145.84.53", 4006, Group("planes"), "id6", HealthcheckInfo("/heartbeat"), Working, Healthy),
          Participant("59.145.84.53", 4007, Group("planes"), "id7", HealthcheckInfo("/heartbeat"), NotWorking, Healthy)
        )
      ).runToFuture,
      5.seconds
    )

    cache.getHealthyParticipantsAssociatedWithGroup(Group("cars")).runToFuture.map(l => l.size should be(0))
  }
}
