package com.gitgub.pandafolks.panda.healthcheck

import com.github.pandafolks.panda.healthcheck.UnsuccessfulHealthCheck
import com.github.pandafolks.panda.participant.Participant
import com.github.pandafolks.panda.participant.dto.ParticipantModificationDto
import com.github.pandafolks.panda.participant.event.ParticipantEvent
import com.github.pandafolks.panda.participant.event.ParticipantEventType.{Disconnected, Joined, ModifiedData}
import monix.eval.Task
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, EitherValues}

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class DistributedHealthCheckServiceImplItTest extends AsyncFlatSpec with DistributedHealthCheckServiceFixture
  with Matchers with ScalaFutures with EitherValues with BeforeAndAfterAll with BeforeAndAfterEach {

  implicit val defaultConfig: PatienceConfig = PatienceConfig(30.seconds, 100.milliseconds)

  override protected def afterAll(): Unit = mongoContainer.stop()

  override protected def beforeEach(): Unit = {
    Await.result(
      Task.parZip2(
        // There is no need to clear nodesCol as these tests are desired to test single node configuration anyway.
        participantEventsAndSequencesConnection.use { case (p, _) => p.db.dropCollection(participantEventsColName) },
        unsuccessfulHealthCheckConnection.use(p => p.db.dropCollection(unsuccessfulHealthCheckColName))
      ).runToFuture,
      10.seconds
    )
    ()
  }

  "DistributedHealthCheckServiceImplItTest#backgroundJob" should "handle typical health check scenario" in {
    // numberOfFailuresNeededToReact equal 2

    val identifier1Healthy = randomString("identifier1")
    val identifier2Healthy = randomString("identifier2")
    val identifier3Unhealthy = randomString("identifier3")

    val firstParticipantsCacheCheck = new AtomicReference[List[Participant]]()
    val secondParticipantsCacheCheck = new AtomicReference[List[Participant]]()
    val thirdParticipantsCacheCheck = new AtomicReference[List[Participant]]()
    val fourthParticipantsCacheCheck = new AtomicReference[List[Participant]]()
    val fifthParticipantsCacheCheck = new AtomicReference[List[Participant]]()

    val firstUnsuccessfulHealthCheckResult = new AtomicReference[List[UnsuccessfulHealthCheck]]()
    val secondUnsuccessfulHealthCheckResult = new AtomicReference[List[UnsuccessfulHealthCheck]]()

    // creating all three participants as working, so the healthcheck will be made on all three of them
    val f = (
      participantEventService.createParticipant(ParticipantModificationDto( // creating identifier1Healthy with wrong healthcheck path
        host = Some("13.204.158.92"), port = Some(3000), groupName = Some("cars"), identifier = Some(identifier1Healthy), healthcheckRoute = Some("badPath"), working = Some(true)
      ))
        >>
        participantEventService.createParticipant(ParticipantModificationDto(
          host = Some("193.207.130.139"), port = Some(3005), groupName = Some("notcars"), identifier = Some(identifier2Healthy), working = Some(true)
        ))
        >>
        participantEventService.createParticipant(ParticipantModificationDto(
          host = Some("193.207.130.140"), port = Some(9991), groupName = Some("notcars"), identifier = Some(identifier3Unhealthy), working = Some(true)
        ))
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> serviceUnderTest.invokePrivate(backgroundJobPrivateMethod()) // first background job run
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> participantsCache.getAllHealthyParticipants.map { res => firstParticipantsCacheCheck.set(res); () }

        // fixing healthcheck path of participant with identifier1Healthy
        >> participantEventService.modifyParticipant(ParticipantModificationDto(identifier = Some(identifier1Healthy), healthcheckRoute = Some("/api/v1/health")))
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> serviceUnderTest.invokePrivate(backgroundJobPrivateMethod()) // second background job run
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> participantsCache.getAllHealthyParticipants.map { res => secondParticipantsCacheCheck.set(res); () }

        // simulating healthcheck fail by setting wrong healthcheck path once again
        >> participantEventService.modifyParticipant(ParticipantModificationDto(identifier = Some(identifier1Healthy), healthcheckRoute = Some("another/bad/path")))
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> serviceUnderTest.invokePrivate(backgroundJobPrivateMethod()) // third background job run
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> participantsCache.getAllHealthyParticipants.map { res => thirdParticipantsCacheCheck.set(res); () } // identifier1Healthy still marked as healthy, because we specified we need two failed healthchecks

        >> serviceUnderTest.invokePrivate(backgroundJobPrivateMethod()) // fourth background job run
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> participantsCache.getAllHealthyParticipants.map { res => fourthParticipantsCacheCheck.set(res); () } // this time identifier1Healthy healthcheck failed two times so the participant has to be marked as `Unhealthy`
        >> unsuccessfulHealthCheckConnection.use(p => p.source.findAll.toListL).map { res => firstUnsuccessfulHealthCheckResult.set(res); () } // there should be only one element with identifier: identifier1Healthy

        // fixing healthcheck path of participant with identifier1Healthy once again
        >> participantEventService.modifyParticipant(ParticipantModificationDto(identifier = Some(identifier1Healthy), healthcheckRoute = Some("/api/v1/health")))
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> serviceUnderTest.invokePrivate(backgroundJobPrivateMethod()) // fifth background job run
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> participantsCache.getAllHealthyParticipants.map { res => fifthParticipantsCacheCheck.set(res); () }
        >> unsuccessfulHealthCheckConnection.use(p => p.source.findAll.toListL).map { res => secondUnsuccessfulHealthCheckResult.set(res); () }
      ).runToFuture

    whenReady(f) { _ =>
      firstParticipantsCacheCheck.get().size should be(1)
      firstParticipantsCacheCheck.get()(0).identifier should be(identifier2Healthy)

      secondParticipantsCacheCheck.get().size should be(2)
      secondParticipantsCacheCheck.get().map(_.identifier) should contain theSameElementsAs List(identifier1Healthy, identifier2Healthy)

      thirdParticipantsCacheCheck.get().size should be(2)
      thirdParticipantsCacheCheck.get().map(_.identifier) should contain theSameElementsAs List(identifier1Healthy, identifier2Healthy)

      fourthParticipantsCacheCheck.get().size should be(1)
      fourthParticipantsCacheCheck.get()(0).identifier should be(identifier2Healthy)
      firstUnsuccessfulHealthCheckResult.get().size should be(1)
      firstUnsuccessfulHealthCheckResult.get()(0).counter should be(2)

      fifthParticipantsCacheCheck.get().size should be(2)
      fifthParticipantsCacheCheck.get().map(_.identifier) should contain theSameElementsAs List(identifier1Healthy, identifier2Healthy)
      secondUnsuccessfulHealthCheckResult.get().size should be(0)
    }
  }

  it should "insert only one Joined/Disconnected event, even if there was NO cache refresh in a meanwhile" in {
    val identifier1 = randomString("identifierEvent1")
    val identifier2 = randomString("identifierEvent2")

    val firstParticipantEventsRetrieve = new AtomicReference[List[ParticipantEvent]]()
    val secondParticipantEventsRetrieve = new AtomicReference[List[ParticipantEvent]]()

    val f = (
      participantEventService.createParticipant(ParticipantModificationDto( // creating identifier1Healthy with wrong healthcheck path
        host = Some("13.204.158.92"), port = Some(3000), groupName = Some("cars"), identifier = Some(identifier1), healthcheckRoute = Some("api/v1/health"), working = Some(true)
      ))
        >> participantEventService.createParticipant(ParticipantModificationDto(
        host = Some("193.207.130.139"), port = Some(3005), groupName = Some("notcars"), identifier = Some(identifier2), working = Some(true)
      ))
        >> unsuccessfulHealthCheckConnection.use(p => p.source.findAll.toListL) // in order to pre-initialize collection and remove flakes
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> serviceUnderTest.invokePrivate(backgroundJobPrivateMethod()) // first background job - both participants got marked as healthy
        >> serviceUnderTest.invokePrivate(backgroundJobPrivateMethod()) // second background job right after
        >> serviceUnderTest.invokePrivate(backgroundJobPrivateMethod()) // and third ...
        >> participantEventsAndSequencesConnection.use { case (p, _) => p.source.findAll.toListL }.map { res => firstParticipantEventsRetrieve.set(res); ()}

        >> participantEventService.modifyParticipant(ParticipantModificationDto(identifier = Some(identifier2), healthcheckRoute = Some("another/bad/path")))
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> serviceUnderTest.invokePrivate(backgroundJobPrivateMethod()) // counter for identifier2 increased, but no event emitted yet
        >> serviceUnderTest.invokePrivate(backgroundJobPrivateMethod()) // counter for identifier2 equal 2 and event about being `Unhealthy` emitted
        >> serviceUnderTest.invokePrivate(backgroundJobPrivateMethod()) // first redundant ...
        >> serviceUnderTest.invokePrivate(backgroundJobPrivateMethod()) // second redundant ...
        >> serviceUnderTest.invokePrivate(backgroundJobPrivateMethod()) // third redundant ...
        >> participantEventService.modifyParticipant(ParticipantModificationDto(identifier = Some(identifier2), healthcheckRoute = Some("healthcheck"))) // fixing healthcheck route
        >> participantsCache.invokePrivate(refreshCachePrivateMethod()) // refreshing cache in order to get a new, fixed healthcheck route for identifier2
        >> serviceUnderTest.invokePrivate(backgroundJobPrivateMethod()) // this recognized that identifier2 is up and emits `Healthy` event for it
        >> participantEventsAndSequencesConnection.use { case (p, _) => p.source.findAll.toListL }.map { res => secondParticipantEventsRetrieve.set(res); ()}
      ).runToFuture

    whenReady(f) { _ =>
      val identifier1EventsFirstRetrieve = firstParticipantEventsRetrieve.get().filter(_.participantIdentifier == identifier1).sortBy(e => -e.eventId.intValue())
      val identifier2EventsFirstRetrieve = firstParticipantEventsRetrieve.get().filter(_.participantIdentifier == identifier2).sortBy(e => -e.eventId.intValue())

      identifier1EventsFirstRetrieve.head.eventType should be(Joined())
      identifier2EventsFirstRetrieve.head.eventType should be(Joined())
      identifier1EventsFirstRetrieve.map(_.eventType).count(_ == Joined()) should be (1)
      identifier2EventsFirstRetrieve.map(_.eventType).count(_ == Joined()) should be (1)


      val identifier1EventsSecondRetrieve = secondParticipantEventsRetrieve.get().filter(_.participantIdentifier == identifier1).sortBy(e => -e.eventId.intValue())
      val identifier2EventsSecondRetrieve = secondParticipantEventsRetrieve.get().filter(_.participantIdentifier == identifier2).sortBy(e => -e.eventId.intValue())

      // nothing changed for identifier1
      identifier1EventsSecondRetrieve.head.eventType should be(Joined())
      identifier1EventsSecondRetrieve.map(_.eventType).count(_ == Joined()) should be (1)

      // last event for identifier2 are: Disconnected -> ModifiedData (which fixes healthcheck path) -> Joined
      identifier2EventsSecondRetrieve.tail.tail.head.eventType should be(Disconnected())
      identifier2EventsSecondRetrieve.tail.head.eventType should be(ModifiedData())
      identifier2EventsSecondRetrieve.head.eventType should be(Joined())

      identifier2EventsSecondRetrieve.map(_.eventType).count(_ == Joined()) should be (2) // the old one from identifier1EventsFirstRetrieve and the latest one
      identifier2EventsSecondRetrieve.map(_.eventType).count(_ == Disconnected()) should be (1)
    }
  }
}
