package com.github.mattszm.panda.participant.event

import com.github.mattszm.panda.participant.dto.ParticipantModificationDto
import com.github.mattszm.panda.participant.{Participant, ParticipantsCache, ParticipantsCacheImpl}
import com.github.mattszm.panda.routes.Group
import com.github.mattszm.panda.utils.ChangeListener
import monix.eval.Task
import monix.execution.Scheduler
import org.mockito.ArgumentMatchers.{any, argThat}
import org.mockito.Mockito.{clearInvocations, mock, times, verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, EitherValues, PrivateMethodTester}

import scala.collection.immutable.Iterable
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class ParticipantsCacheImplItTest extends AsyncFlatSpec with ParticipantEventFixture with Matchers with ScalaFutures
  with EitherValues with BeforeAndAfterAll with BeforeAndAfterEach with PrivateMethodTester {
  implicit val scheduler: Scheduler = Scheduler.io("participant-cache-it-test")

  implicit val defaultConfig: PatienceConfig = PatienceConfig(30.seconds, 100.milliseconds)

  override protected def afterAll(): Unit = mongoContainer.stop()

  override protected def beforeEach(): Unit = Await.result(participantEventsAndSequencesConnection.use {
    case (p, _) => p.db.dropCollection(participantEventsColName)
  }.runToFuture, 5.seconds)

  private def createParticipantsCacheWithMockedListener(): (ParticipantsCache, ChangeListener[Participant]) = {
    val cache = Await.result(ParticipantsCacheImpl(
      participantEventService = participantEventService,
      List.empty,
      -1 // background refresh job disabled
    ).runToFuture, 5.seconds)

    val participantChangeListener = mock(classOf[ChangeListener[Participant]])
    when(participantChangeListener.notifyAboutAdd(any[Iterable[Participant]]())) thenReturn Task.unit
    when(participantChangeListener.notifyAboutRemove(any[Iterable[Participant]]())) thenReturn Task.unit

    Await.result(cache.registerListener(participantChangeListener).runToFuture, 5.seconds)
    clearInvocations(participantChangeListener)

    (cache, participantChangeListener)
  }

  "ParticipantsCacheImpl#refreshCache" should "take participants' events, process them and replace the cache state. There should be also listeners invoked. (check after single refresh)" in {
    val (cache, listener) = createParticipantsCacheWithMockedListener()

    val refreshCache = PrivateMethod[Task[Unit]](Symbol("refreshCache"))

    val identifier1 = randomString("identifier1")
    val identifier2 = randomString("identifier2")
    val participantEvent1 = ParticipantModificationDto(
      host = Some("127.0.0.1"), port = Some(1001), groupName = Some("cars"), identifier = Some(identifier1), heartbeatRoute = Option.empty, working = Some(false)
    )
    val participantEvent2 = ParticipantModificationDto(
      host = Some("127.0.0.2"), port = Some(1002), groupName = Some("cars"), identifier = Some(identifier2), heartbeatRoute = Option.empty, working = Some(true)
    )

    val f = (Task.sequence(List(
      participantEventService.createParticipant(participantEvent1),
      participantEventService.createParticipant(participantEvent2)
    ))
      >> cache.invokePrivate(refreshCache())
      >> Task.parZip3(
      cache.getAllGroups,
      cache.getParticipantsAssociatedWithGroup(Group("cars")),
      cache.getWorkingParticipantsAssociatedWithGroup(Group("cars")),
    )).runToFuture

    whenReady(f) { res =>
      verify(listener, times(1)).notifyAboutAdd(
        argThat((list: List[Participant]) => list.map(_.identifier).toSet == Set(identifier1, identifier2)))
      verify(listener, times(1)).notifyAboutRemove(Set.empty)

      res._1.size should be(1)
      res._1.head.name should be("cars")

      res._2.size should be(2)
      res._2.map(_.identifier) should contain theSameElementsAs List(identifier1, identifier2)
      res._3.size should be(1)
      res._3.head.identifier should be(identifier2)

    }
  }

  it should "take participants' events, process them and replace the cache state. There should be also listeners invoked. (check after double refresh)" in {
    val (cache, listener) = createParticipantsCacheWithMockedListener()

    val refreshCache = PrivateMethod[Task[Unit]](Symbol("refreshCache"))

    val identifier1 = randomString("identifier3")
    val identifier2 = randomString("identifier4")
    val identifier3 = randomString("identifier5")
    val participantEvent1 = ParticipantModificationDto(
      host = Some("127.0.0.1"), port = Some(1001), groupName = Some("cars"), identifier = Some(identifier1), heartbeatRoute = Option.empty, working = Some(false)
    )
    val participantEvent2 = ParticipantModificationDto(
      host = Some("127.0.0.2"), port = Some(1002), groupName = Some("cars"), identifier = Some(identifier2), heartbeatRoute = Option.empty, working = Some(true)
    )
    val participantEvent3 = ParticipantModificationDto(
      host = Some("127.0.0.3"), port = Some(1003), groupName = Some("cars"), identifier = Some(identifier3), heartbeatRoute = Option.empty, working = Some(true)
    )

    val f = (Task.sequence(List(
      participantEventService.createParticipant(participantEvent1),
      participantEventService.createParticipant(participantEvent2)
    ))
      >> cache.invokePrivate(refreshCache())
      >> Task.now(clearInvocations(listener))
      >> Task.sequence(List(
      participantEventService.modifyParticipant(ParticipantModificationDto(
        host = Some("127.1.1.1"), working = Some(false), identifier = Some(identifier2) // modification of seconds participant and turning it off
      )),
      participantEventService.createParticipant(participantEvent3) // creation of third participant (working)
    ))
      >> cache.invokePrivate(refreshCache())
      >> Task.parZip3(
      cache.getAllGroups,
      cache.getParticipantsAssociatedWithGroup(Group("cars")),
      cache.getWorkingParticipantsAssociatedWithGroup(Group("cars")),
    )).runToFuture

    whenReady(f) { res =>
      verify(listener, times(1)).notifyAboutAdd(
        argThat((list: List[Participant]) => list.map(_.identifier).toSet == Set(identifier1, identifier2, identifier3))) // participant3 is new, participant2 changed and participant 1 is old, unchanged
      verify(listener, times(1)).notifyAboutRemove(
        argThat((set: Set[Participant]) => set.head.identifier == identifier2)) // because participant2 changed

      res._1.size should be(1)
      res._1.head.name should be("cars")

      res._2.size should be(3)
      res._2.map(_.identifier) should contain theSameElementsAs List(identifier1, identifier2, identifier3)
      res._3.size should be(1)
      res._3.head.identifier should be(identifier3)

    }
  }

  it should "it should discard participants that had Removed() event emitted and not Created as a follow-up. The listeners should be notified about this change." in {
    val (cache, listener) = createParticipantsCacheWithMockedListener()

    val refreshCache = PrivateMethod[Task[Unit]](Symbol("refreshCache"))

    val identifier1 = randomString("identifier6")
    val identifier2 = randomString("identifier7")
    val participantEvent1 = ParticipantModificationDto(
      host = Some("127.0.0.1"), port = Some(1001), groupName = Some("cars"), identifier = Some(identifier1), heartbeatRoute = Option.empty, working = Some(true)
    )
    val participantEvent2 = ParticipantModificationDto(
      host = Some("127.0.0.2"), port = Some(1002), groupName = Some("cars"), identifier = Some(identifier2), heartbeatRoute = Option.empty, working = Some(true)
    )

    val f = (Task.sequence(List(
      participantEventService.createParticipant(participantEvent1),
      participantEventService.createParticipant(participantEvent2)
    ))
      >> cache.invokePrivate(refreshCache())
      >> Task.now(clearInvocations(listener))
      >> participantEventService.removeParticipant(identifier2)
      >> cache.invokePrivate(refreshCache())
      >> Task.parZip3(
      cache.getAllGroups,
      cache.getParticipantsAssociatedWithGroup(Group("cars")),
      cache.getWorkingParticipantsAssociatedWithGroup(Group("cars")),
    )).runToFuture

    whenReady(f) { res =>
      verify(listener, times(1)).notifyAboutAdd(
        argThat((list: List[Participant]) => list.map(_.identifier).toSet == Set(identifier1))) // participant1 is still valid
      verify(listener, times(1)).notifyAboutRemove(
        argThat((set: Set[Participant]) => set.head.identifier == identifier2)) // participant2 was present in cache before refresh, but it is not anymore

      res._1.size should be(1)
      res._1.head.name should be("cars")

      res._2.size should be(1)
      res._2.map(_.identifier) should contain theSameElementsAs List(identifier1)
      res._3.size should be(1)
      res._3.head.identifier should be(identifier1)
    }
  }

}
