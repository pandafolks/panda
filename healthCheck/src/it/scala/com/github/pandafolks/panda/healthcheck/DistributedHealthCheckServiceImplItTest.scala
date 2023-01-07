package com.github.pandafolks.panda.healthcheck

import com.github.pandafolks.panda.backgroundjobsregistry.InMemoryBackgroundJobsRegistryImpl
import com.github.pandafolks.panda.nodestracker.{Job, Node}
import com.github.pandafolks.panda.participant.event.ParticipantEvent
import com.github.pandafolks.panda.participant.event.ParticipantEventType.{Disconnected, Joined, ModifiedData}
import com.github.pandafolks.panda.participant.{Participant, ParticipantModificationPayload}
import monix.eval.Task
import org.bson.types.ObjectId
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, EitherValues}

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class DistributedHealthCheckServiceImplItTest
    extends AsyncFlatSpec
    with DistributedHealthCheckServiceFixture
    with Matchers
    with ScalaFutures
    with EitherValues
    with BeforeAndAfterAll
    with BeforeAndAfterEach {

  implicit val defaultConfig: PatienceConfig = PatienceConfig(30.seconds, 100.milliseconds)

  override protected def afterAll(): Unit = mongoContainer.stop()

  override protected def beforeEach(): Unit = {
    Await.result(
      Task
        .parZip4(
          // There is no need to clear nodesCol as these tests are desired to test single node configuration anyway.
          participantEventsAndSequencesConnection.use { case (p, _) => p.db.dropCollection(participantEventsColName) },
          unsuccessfulHealthCheckConnection.use(p => p.db.dropCollection(unsuccessfulHealthCheckColName)),
          nodesConnection.use(p => p.db.dropCollection(nodesColName)),
          jobsConnection.use(p => p.db.dropCollection(jobsColName))
        )
        .runToFuture,
      10.seconds
    )
    ()
  }

  "DistributedHealthCheckServiceImplItTest#healthCheckBackgroundJob" should "handle typical health check scenario" in {
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
      participantEventService.createParticipant(
        ParticipantModificationPayload( // creating identifier1Healthy with wrong healthcheck path
          host = Some("13.204.158.92"),
          port = Some(3000),
          groupName = Some("cars"),
          identifier = Some(identifier1Healthy),
          healthcheckRoute = Some("badPath"),
          working = Some(true)
        )
      )
        >>
          participantEventService.createParticipant(
            ParticipantModificationPayload(
              host = Some("193.207.130.139"),
              port = Some(3005),
              groupName = Some("notcars"),
              identifier = Some(identifier2Healthy),
              working = Some(true)
            )
          )
          >>
          participantEventService.createParticipant(
            ParticipantModificationPayload(
              host = Some("193.207.130.140"),
              port = Some(9991),
              groupName = Some("notcars"),
              identifier = Some(identifier3Unhealthy),
              working = Some(true)
            )
          )
          >> participantsCache.invokePrivate(refreshCachePrivateMethod())
          >> serviceUnderTest.invokePrivate(healthCheckBackgroundJobPrivateMethod()) // first background job run
          >> participantsCache.invokePrivate(refreshCachePrivateMethod())
          >> participantsCache.getAllHealthyParticipants.map { res => firstParticipantsCacheCheck.set(res); () }

          // fixing healthcheck path of participant with identifier1Healthy
          >> participantEventService.modifyParticipant(
            ParticipantModificationPayload(
              identifier = Some(identifier1Healthy),
              healthcheckRoute = Some("/api/v1/health")
            )
          )
          >> participantsCache.invokePrivate(refreshCachePrivateMethod())
          >> serviceUnderTest.invokePrivate(healthCheckBackgroundJobPrivateMethod()) // second background job run
          >> participantsCache.invokePrivate(refreshCachePrivateMethod())
          >> participantsCache.getAllHealthyParticipants.map { res => secondParticipantsCacheCheck.set(res); () }

          // simulating healthcheck fail by setting wrong healthcheck path once again
          >> participantEventService.modifyParticipant(
            ParticipantModificationPayload(
              identifier = Some(identifier1Healthy),
              healthcheckRoute = Some("another/bad/path")
            )
          )
          >> participantsCache.invokePrivate(refreshCachePrivateMethod())
          >> serviceUnderTest.invokePrivate(healthCheckBackgroundJobPrivateMethod()) // third background job run
          >> participantsCache.invokePrivate(refreshCachePrivateMethod())
          >> participantsCache.getAllHealthyParticipants.map { res =>
            thirdParticipantsCacheCheck.set(res); ()
          } // identifier1Healthy still marked as healthy, because we specified we need two failed healthchecks

          >> serviceUnderTest.invokePrivate(healthCheckBackgroundJobPrivateMethod()) // fourth background job run
          >> participantsCache.invokePrivate(refreshCachePrivateMethod())
          >> participantsCache.getAllHealthyParticipants.map { res =>
            fourthParticipantsCacheCheck.set(res); ()
          } // this time identifier1Healthy healthcheck failed two times so the participant has to be marked as `Unhealthy`
          >> unsuccessfulHealthCheckConnection.use(p => p.source.findAll.toListL).map { res =>
            firstUnsuccessfulHealthCheckResult.set(res); ()
          } // there should be only one element with identifier: identifier1Healthy

          // fixing healthcheck path of participant with identifier1Healthy once again
          >> participantEventService.modifyParticipant(
            ParticipantModificationPayload(
              identifier = Some(identifier1Healthy),
              healthcheckRoute = Some("/api/v1/health")
            )
          )
          >> participantsCache.invokePrivate(refreshCachePrivateMethod())
          >> serviceUnderTest.invokePrivate(healthCheckBackgroundJobPrivateMethod()) // fifth background job run
          >> serviceUnderTest.invokePrivate(healthCheckBackgroundJobPrivateMethod()) // redundant background job run
          >> serviceUnderTest.invokePrivate(healthCheckBackgroundJobPrivateMethod()) // redundant background job run
          >> serviceUnderTest.invokePrivate(healthCheckBackgroundJobPrivateMethod()) // redundant background job run

          >> participantsCache.invokePrivate(refreshCachePrivateMethod())
          >> participantsCache.getAllHealthyParticipants.map { res => fifthParticipantsCacheCheck.set(res); () }
          >> unsuccessfulHealthCheckConnection.use(p => p.source.findAll.toListL).map { res =>
            secondUnsuccessfulHealthCheckResult.set(res); ()
          }
    ).runToFuture

    whenReady(f) { _ =>
      firstParticipantsCacheCheck.get().size should be(1)
      firstParticipantsCacheCheck.get().head.identifier should be(identifier2Healthy)

      secondParticipantsCacheCheck.get().size should be(2)
      secondParticipantsCacheCheck.get().map(_.identifier) should contain theSameElementsAs List(
        identifier1Healthy,
        identifier2Healthy
      )

      thirdParticipantsCacheCheck.get().size should be(2)
      thirdParticipantsCacheCheck.get().map(_.identifier) should contain theSameElementsAs List(
        identifier1Healthy,
        identifier2Healthy
      )

      fourthParticipantsCacheCheck.get().size should be(1)
      fourthParticipantsCacheCheck.get().head.identifier should be(identifier2Healthy)
      firstUnsuccessfulHealthCheckResult.get().size should be(1)
      firstUnsuccessfulHealthCheckResult.get().head.counter should be(2)

      fifthParticipantsCacheCheck.get().size should be(2)
      fifthParticipantsCacheCheck.get().map(_.identifier) should contain theSameElementsAs List(
        identifier1Healthy,
        identifier2Healthy
      )
      secondUnsuccessfulHealthCheckResult.get().size should be(0)
    }
  }

  it should "insert only one Joined/Disconnected event, even if there was NO cache refresh in a meanwhile" in {
    val identifier1 = randomString("identifierEvent1")
    val identifier2 = randomString("identifierEvent2")

    val firstParticipantEventsRetrieve = new AtomicReference[List[ParticipantEvent]]()
    val secondParticipantEventsRetrieve = new AtomicReference[List[ParticipantEvent]]()

    val f = (
      participantEventService.createParticipant(
        ParticipantModificationPayload( // creating identifier1Healthy with wrong healthcheck path
          host = Some("13.204.158.92"),
          port = Some(3000),
          groupName = Some("cars"),
          identifier = Some(identifier1),
          healthcheckRoute = Some("api/v1/health"),
          working = Some(true)
        )
      ) >> participantEventService.createParticipant(
        ParticipantModificationPayload(
          host = Some("193.207.130.139"),
          port = Some(3005),
          groupName = Some("notcars"),
          identifier = Some(identifier2),
          working = Some(true)
        )
      )
        >> unsuccessfulHealthCheckConnection.use(p => p.source.findAll.toListL) // in order to pre-initialize collection and remove flakes
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> serviceUnderTest.invokePrivate(
          healthCheckBackgroundJobPrivateMethod()
        ) // first background job - both participants got marked as healthy
        >> serviceUnderTest.invokePrivate(healthCheckBackgroundJobPrivateMethod()) // second background job right after
        >> serviceUnderTest.invokePrivate(healthCheckBackgroundJobPrivateMethod()) // and third ...
        >> participantEventsAndSequencesConnection.use { case (p, _) => p.source.findAll.toListL }.map { res =>
          firstParticipantEventsRetrieve.set(res); ()
        }

        >> participantEventService.modifyParticipant(
          ParticipantModificationPayload(identifier = Some(identifier2), healthcheckRoute = Some("another/bad/path"))
        )
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> serviceUnderTest.invokePrivate(
          healthCheckBackgroundJobPrivateMethod()
        ) // counter for identifier2 increased, but no event emitted yet
        >> serviceUnderTest.invokePrivate(
          healthCheckBackgroundJobPrivateMethod()
        ) // counter for identifier2 equal 2 and event about being `Unhealthy` emitted
        >> serviceUnderTest.invokePrivate(healthCheckBackgroundJobPrivateMethod()) // first redundant ...
        >> serviceUnderTest.invokePrivate(healthCheckBackgroundJobPrivateMethod()) // second redundant ...
        >> serviceUnderTest.invokePrivate(healthCheckBackgroundJobPrivateMethod()) // third redundant ...
        >> participantEventService.modifyParticipant(
          ParticipantModificationPayload(identifier = Some(identifier2), healthcheckRoute = Some("healthcheck"))
        ) // fixing healthcheck route
        >> participantsCache.invokePrivate(
          refreshCachePrivateMethod()
        ) // refreshing cache in order to get a new, fixed healthcheck route for identifier2
        >> serviceUnderTest.invokePrivate(
          healthCheckBackgroundJobPrivateMethod()
        ) // this recognized that identifier2 is up and emits `Healthy` event for it
        >> participantEventsAndSequencesConnection.use { case (p, _) => p.source.findAll.toListL }.map { res =>
          secondParticipantEventsRetrieve.set(res); ()
        }
    ).runToFuture

    whenReady(f) { _ =>
      val identifier1EventsFirstRetrieve = firstParticipantEventsRetrieve
        .get()
        .filter(_.participantIdentifier == identifier1)
        .sortBy(e => -e.eventId.intValue())
      val identifier2EventsFirstRetrieve = firstParticipantEventsRetrieve
        .get()
        .filter(_.participantIdentifier == identifier2)
        .sortBy(e => -e.eventId.intValue())

      identifier1EventsFirstRetrieve.head.eventType should be(Joined())
      identifier2EventsFirstRetrieve.head.eventType should be(Joined())
      identifier1EventsFirstRetrieve.map(_.eventType).count(_ == Joined()) should be(1)
      identifier2EventsFirstRetrieve.map(_.eventType).count(_ == Joined()) should be(1)

      val identifier1EventsSecondRetrieve = secondParticipantEventsRetrieve
        .get()
        .filter(_.participantIdentifier == identifier1)
        .sortBy(e => -e.eventId.intValue())
      val identifier2EventsSecondRetrieve = secondParticipantEventsRetrieve
        .get()
        .filter(_.participantIdentifier == identifier2)
        .sortBy(e => -e.eventId.intValue())

      // nothing changed for identifier1
      identifier1EventsSecondRetrieve.head.eventType should be(Joined())
      identifier1EventsSecondRetrieve.map(_.eventType).count(_ == Joined()) should be(1)

      // last event for identifier2 are: Disconnected -> ModifiedData (which fixes healthcheck path) -> Joined
      identifier2EventsSecondRetrieve.tail.tail.head.eventType should be(Disconnected())
      identifier2EventsSecondRetrieve.tail.head.eventType should be(ModifiedData())
      identifier2EventsSecondRetrieve.head.eventType should be(Joined())

      identifier2EventsSecondRetrieve.map(_.eventType).count(_ == Joined()) should be(
        2
      ) // the old one from identifier1EventsFirstRetrieve and the latest one
      identifier2EventsSecondRetrieve.map(_.eventType).count(_ == Disconnected()) should be(1)
    }
  }

  "DistributedHealthCheckServiceImplItTest#markAsNotWorkingBackgroundJob" should "handle typical scenario" in {
    // participantIsMarkedAsTurnedOffDelay equals 5
    // participantIsMarkedAsRemovedDelay equals 10

    val identifier1 = randomString("markAsNotWorkingBackgroundJob1")
    val identifier2 = randomString("markAsNotWorkingBackgroundJob2")
    val identifier3 = randomString("markAsNotWorkingBackgroundJob3")

    val firstParticipantsCacheCheck = new AtomicReference[List[Participant]]()
    val secondParticipantsCacheCheck = new AtomicReference[List[Participant]]()
    val thirdParticipantsCacheCheck = new AtomicReference[List[Participant]]()
    val fourthParticipantsCacheCheck = new AtomicReference[List[Participant]]()
    val fifthParticipantsCacheCheck = new AtomicReference[List[Participant]]()
    val sixthParticipantsCacheCheck = new AtomicReference[List[Participant]]()
    val seventhParticipantsCacheCheck = new AtomicReference[List[Participant]]()

    val firstUnsuccessfulHealthCheck = new AtomicReference[List[UnsuccessfulHealthCheck]]()
    val secondUnsuccessfulHealthCheck = new AtomicReference[List[UnsuccessfulHealthCheck]]()
    val thirdUnsuccessfulHealthCheck = new AtomicReference[List[UnsuccessfulHealthCheck]]()
    val fourthUnsuccessfulHealthCheck = new AtomicReference[List[UnsuccessfulHealthCheck]]()
    val fifthUnsuccessfulHealthCheck = new AtomicReference[List[UnsuccessfulHealthCheck]]()
    val sixthUnsuccessfulHealthCheck = new AtomicReference[List[UnsuccessfulHealthCheck]]()
    val seventhUnsuccessfulHealthCheck = new AtomicReference[List[UnsuccessfulHealthCheck]]()

    val f = (
      participantEventService.createParticipant(
        ParticipantModificationPayload(
          host = Some("13.204.158.92"),
          port = Some(3000),
          groupName = Some("cars"),
          identifier = Some(identifier1),
          healthcheckRoute = Some("/api/v1/health"),
          working = Some(true)
        )
      ) >>
        participantEventService.createParticipant(
          ParticipantModificationPayload(
            host = Some("193.207.130.139"),
            port = Some(3005),
            groupName = Some("notcars"),
            identifier = Some(identifier2),
            working = Some(true)
          )
        ) >>
        participantEventService.createParticipant( // same host, port, etc.. but we do not care cuz it's only testing - participant identity is based on the identifier either way
          ParticipantModificationPayload(
            host = Some("193.207.130.139"),
            port = Some(3005),
            groupName = Some("notcars"),
            identifier = Some(identifier3),
            working = Some(true)
          )
        )
        >> unsuccessfulHealthCheckConnection.use(p => p.source.findAll.toListL)
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> serviceUnderTest.invokePrivate(healthCheckBackgroundJobPrivateMethod())
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> participantsCache.getAllWorkingParticipants.map { r => firstParticipantsCacheCheck.set(r); () }
        >> unsuccessfulHealthCheckConnection.use(p => p.source.findAll.toListL).map { r =>
          firstUnsuccessfulHealthCheck.set(r); ()
        }

        >> participantEventService.modifyParticipant(
          ParticipantModificationPayload(
            identifier = Some(identifier1),
            healthcheckRoute = Some("notWorking1")
          )
        )
        >> participantEventService.modifyParticipant(
          ParticipantModificationPayload(
            identifier = Some(identifier2),
            healthcheckRoute = Some("notWorking2")
          )
        )

        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> serviceUnderTest.invokePrivate(healthCheckBackgroundJobPrivateMethod())
        >> serviceUnderTest.invokePrivate(healthCheckBackgroundJobPrivateMethod())
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> participantsCache.getAllWorkingParticipants.map { r => secondParticipantsCacheCheck.set(r); () }
        >> unsuccessfulHealthCheckConnection.use(p => p.source.findAll.toListL).map { r =>
          secondUnsuccessfulHealthCheck.set(r); ()
        }

        >> serviceUnderTest.invokePrivate(
          markAsNotWorkingBackgroundJobPrivateMethod()
        ) // nothing changes because not enough time passed
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> participantsCache.getAllWorkingParticipants.map { r => thirdParticipantsCacheCheck.set(r); () }
        >> unsuccessfulHealthCheckConnection.use(p => p.source.findAll.toListL).map { r =>
          thirdUnsuccessfulHealthCheck.set(r); ()
        }

        >> Task.sleep(5.seconds)
        >> serviceUnderTest.invokePrivate(markAsNotWorkingBackgroundJobPrivateMethod())
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> participantsCache.getAllParticipants.map { r => fourthParticipantsCacheCheck.set(r); () }
        >> unsuccessfulHealthCheckConnection.use(p => p.source.findAll.toListL).map { r =>
          fourthUnsuccessfulHealthCheck.set(r); ()
        }

        // redundant:
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> serviceUnderTest.invokePrivate(healthCheckBackgroundJobPrivateMethod())
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())

        // making identifier3 not working
        >> participantEventService.modifyParticipant(
          ParticipantModificationPayload(
            identifier = Some(identifier3),
            healthcheckRoute = Some("notWorking3")
          )
        )
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> serviceUnderTest.invokePrivate(healthCheckBackgroundJobPrivateMethod())
        >> serviceUnderTest.invokePrivate(markAsNotWorkingBackgroundJobPrivateMethod())
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> participantsCache.getAllParticipants.map { r => fifthParticipantsCacheCheck.set(r); () }
        >> unsuccessfulHealthCheckConnection.use(p => p.source.findAll.toListL).map { r =>
          fifthUnsuccessfulHealthCheck.set(r); ()
        }

        >> serviceUnderTest.invokePrivate(healthCheckBackgroundJobPrivateMethod())
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> serviceUnderTest.invokePrivate(markAsNotWorkingBackgroundJobPrivateMethod())
        >> participantsCache.getAllParticipants.map { r => sixthParticipantsCacheCheck.set(r); () }
        >> unsuccessfulHealthCheckConnection.use(p => p.source.findAll.toListL).map { r =>
          sixthUnsuccessfulHealthCheck.set(r); ()
        }

        >> Task.sleep(5.seconds)
        >> serviceUnderTest.invokePrivate(markAsNotWorkingBackgroundJobPrivateMethod())
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> participantsCache.getAllParticipants.map { r => seventhParticipantsCacheCheck.set(r); () }
        >> unsuccessfulHealthCheckConnection.use(p => p.source.findAll.toListL).map { r =>
          seventhUnsuccessfulHealthCheck.set(r); ()
        }
    ).runToFuture

    whenReady(f) { _ =>
      // all 3 participants are healthy
      firstParticipantsCacheCheck.get().size should be(3)
      firstParticipantsCacheCheck.get().map(_.isWorkingAndHealthy) should be(List(true, true, true))
      firstUnsuccessfulHealthCheck.get().size should be(0)

      // there were 2 health-checks performed - our service needs 2 to mark participant as not healthy
      secondParticipantsCacheCheck.get().size should be(3)
      val mapped1 = secondParticipantsCacheCheck
        .get()
        .foldLeft(Map.empty[String, Participant])((prev, it) => prev + (it.identifier -> it))
      mapped1(identifier1).isHealthy should be(false)
      mapped1(identifier2).isHealthy should be(false)
      mapped1(identifier3).isHealthy should be(true)
      secondUnsuccessfulHealthCheck.get().size should be(2)
      secondUnsuccessfulHealthCheck.get().map(_.counter) should be(List(2, 2))
      secondUnsuccessfulHealthCheck.get().map(_.turnedOff) should be(List(false, false))

      // nothing changes because not enough time passed
      thirdParticipantsCacheCheck.get().size should be(3)
      val mapped2 = thirdParticipantsCacheCheck
        .get()
        .foldLeft(Map.empty[String, Participant])((prev, it) => prev + (it.identifier -> it))
      mapped2(identifier1).isHealthy should be(false)
      mapped2(identifier2).isHealthy should be(false)
      mapped2(identifier3).isHealthy should be(true)
      thirdUnsuccessfulHealthCheck.get().size should be(2)
      thirdUnsuccessfulHealthCheck.get().map(_.counter) should be(List(2, 2))
      thirdUnsuccessfulHealthCheck.get().map(_.turnedOff) should be(List(false, false))

      // after 5 seconds
      fourthParticipantsCacheCheck.get().size should be(3)
      fourthParticipantsCacheCheck.get().count(_.isWorking) should be(1)
      fourthParticipantsCacheCheck.get().count(_.isHealthy) should be(1)
      fourthUnsuccessfulHealthCheck.get().size should be(2)
      fourthUnsuccessfulHealthCheck.get().map(_.counter) should be(List(2, 2))
      fourthUnsuccessfulHealthCheck.get().map(_.turnedOff) should be(List(true, true))

      // modified participant with identifier3
      fifthParticipantsCacheCheck.get().size should be(3)
      fifthParticipantsCacheCheck.get().count(_.isWorking) should be(1)
      fifthParticipantsCacheCheck.get().count(_.isHealthy) should be(1)
      fifthUnsuccessfulHealthCheck.get().size should be(3)
      val mapped3 = fifthUnsuccessfulHealthCheck
        .get()
        .foldLeft(Map.empty[String, UnsuccessfulHealthCheck])((prev, it) => prev + (it.identifier -> it))
      mapped3(identifier1).counter should be(2)
      mapped3(identifier2).counter should be(2)
      mapped3(identifier3).counter should be(1)
      mapped3(identifier1).turnedOff should be(true)
      mapped3(identifier2).turnedOff should be(true)
      mapped3(identifier3).turnedOff should be(false)

      // not enough time passed so sixthUnsuccessfulHealthCheck state is equal to fifthUnsuccessfulHealthCheck (only counter changed from 1 to 2), but the second healthcheck for identifier3 already failed, so the participant is marked as not healthy
      sixthParticipantsCacheCheck.get().size should be(3)
      sixthParticipantsCacheCheck.get().count(_.isWorking) should be(1)
      sixthParticipantsCacheCheck.get().count(_.isHealthy) should be(0)
      sixthUnsuccessfulHealthCheck.get().size should be(3)
      val mapped4 = sixthUnsuccessfulHealthCheck
        .get()
        .foldLeft(Map.empty[String, UnsuccessfulHealthCheck])((prev, it) => prev + (it.identifier -> it))
      mapped4(identifier1).counter should be(2)
      mapped4(identifier2).counter should be(2)
      mapped4(identifier3).counter should be(2)
      mapped4(identifier1).turnedOff should be(true)
      mapped4(identifier2).turnedOff should be(true)
      mapped4(identifier3).turnedOff should be(false)

      // after next 5 seconds
      seventhParticipantsCacheCheck.get().size should be(1)
      seventhParticipantsCacheCheck.get().head.identifier should be(identifier3)
      seventhParticipantsCacheCheck.get().count(_.isWorking) should be(0)
      seventhParticipantsCacheCheck.get().count(_.isHealthy) should be(0)
      seventhUnsuccessfulHealthCheck.get().size should be(1)
      seventhUnsuccessfulHealthCheck.get().head.counter should be(2)
      seventhUnsuccessfulHealthCheck.get().head.identifier should be(identifier3)
      seventhUnsuccessfulHealthCheck.get().head.turnedOff should be(true)
    }
  }

  it should "skip the whole logic if the current node is not responsible for performing this background job" in {
    // participantIsMarkedAsTurnedOffDelay equals 5
    // participantIsMarkedAsRemovedDelay equals 10

    val identifier1 = randomString("markAsNotWorkingBackgroundJob4")
    val identifier2 = randomString("markAsNotWorkingBackgroundJob5")
    val identifier3 = randomString("markAsNotWorkingBackgroundJob6")

    val firstUnsuccessfulHealthCheck = new AtomicReference[List[UnsuccessfulHealthCheck]]()
    val secondUnsuccessfulHealthCheck = new AtomicReference[List[UnsuccessfulHealthCheck]]()
    val thirdUnsuccessfulHealthCheck = new AtomicReference[List[UnsuccessfulHealthCheck]]()

    val fakeNodeId = ObjectId.get()

    val f = (
      nodesConnection.use(p => p.single.insertOne(Node(fakeNodeId, System.currentTimeMillis() + 10 * 1000))) // fake node
        >> jobsConnection.use(p => p.single.insertOne(Job("MarkingParticipantsAsEitherTurnedOffOrRemoved", fakeNodeId)))
        >> participantEventService.createParticipant(
          ParticipantModificationPayload(
            host = Some("13.204.158.92"),
            port = Some(3000),
            groupName = Some("cars"),
            identifier = Some(identifier1),
            healthcheckRoute = Some("/api/v1/health"),
            working = Some(true)
          )
        ) >>
        participantEventService.createParticipant(
          ParticipantModificationPayload(
            host = Some("193.207.130.139"),
            port = Some(3005),
            groupName = Some("notcars"),
            identifier = Some(identifier2),
            working = Some(true)
          )
        ) >>
        participantEventService
          .createParticipant( // same host, port, etc.. but we do not care cuz it's only testing - participant identity is based on the identifier either way
            ParticipantModificationPayload(
              host = Some("193.207.130.139"),
              port = Some(3005),
              groupName = Some("notcars"),
              identifier = Some(identifier3),
              working = Some(true)
            )
          )
        >> unsuccessfulHealthCheckConnection.use(p => p.source.findAll.toListL)
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> unsuccessfulHealthCheckConnection.use(p =>
          p.single.insertMany(
            Seq( // inserting manually entries that should be detected by the background job
              UnsuccessfulHealthCheck(
                identifier = identifier1,
                counter = 2,
                lastUpdateTimestamp = System.currentTimeMillis() - 20 * 1000,
                turnedOff = false
              ),
              UnsuccessfulHealthCheck(
                identifier = identifier2,
                counter = 2,
                lastUpdateTimestamp = System.currentTimeMillis() - 30 * 1000,
                turnedOff = true
              )
            )
          )
        )
        >> unsuccessfulHealthCheckConnection.use(p => p.source.findAll.toListL).map { r =>
          firstUnsuccessfulHealthCheck.set(r); ()
        }

        >> serviceUnderTest.invokePrivate(markAsNotWorkingBackgroundJobPrivateMethod())
        >> unsuccessfulHealthCheckConnection.use(p => p.source.findAll.toListL).map { r =>
          secondUnsuccessfulHealthCheck.set(r); ()
        }

        >> serviceUnderTest.invokePrivate(markAsNotWorkingBackgroundJobPrivateMethod())
        >> serviceUnderTest.invokePrivate(markAsNotWorkingBackgroundJobPrivateMethod())
        >> unsuccessfulHealthCheckConnection.use(p => p.source.findAll.toListL).map { r =>
          thirdUnsuccessfulHealthCheck.set(r); ()
        }
    ).runToFuture

    whenReady(f) { _ =>
      // nothing can change...
      firstUnsuccessfulHealthCheck.get() should be(secondUnsuccessfulHealthCheck.get())
      firstUnsuccessfulHealthCheck.get() should be(thirdUnsuccessfulHealthCheck.get())
    }
  }

  it should "work when there is no markAsTurnedOff setting" in { // the participants are removed immediately
    val serviceUnderTest = new DistributedHealthCheckServiceImpl(
      participantEventService,
      participantsCache,
      nodeTrackerService,
      unsuccessfulHealthCheckDao,
      new ClientStub(),
      new InMemoryBackgroundJobsRegistryImpl(scheduler)
    )(HealthCheckConfig(-1, 2, Some(5), Some(5), Option.empty)) // these values are equal so only markAsRemoved is used

    val identifier1 = randomString("markAsNotWorkingBackgroundJob7")
    val identifier2 = randomString("markAsNotWorkingBackgroundJob8")
    val identifier3 = randomString("markAsNotWorkingBackgroundJob9")

    val firstParticipantsCacheCheck = new AtomicReference[List[Participant]]()
    val secondParticipantsCacheCheck = new AtomicReference[List[Participant]]()
    val thirdParticipantsCacheCheck = new AtomicReference[List[Participant]]()
    val fourthParticipantsCacheCheck = new AtomicReference[List[Participant]]()

    val firstUnsuccessfulHealthCheck = new AtomicReference[List[UnsuccessfulHealthCheck]]()
    val secondUnsuccessfulHealthCheck = new AtomicReference[List[UnsuccessfulHealthCheck]]()
    val thirdUnsuccessfulHealthCheck = new AtomicReference[List[UnsuccessfulHealthCheck]]()
    val fourthUnsuccessfulHealthCheck = new AtomicReference[List[UnsuccessfulHealthCheck]]()

    val f = (
      participantEventService.createParticipant(
        ParticipantModificationPayload(
          host = Some("13.204.158.92"),
          port = Some(3000),
          groupName = Some("cars"),
          identifier = Some(identifier1),
          healthcheckRoute = Some("/api/v1/health"),
          working = Some(true)
        )
      ) >>
        participantEventService.createParticipant(
          ParticipantModificationPayload(
            host = Some("193.207.130.139"),
            port = Some(3005),
            groupName = Some("notcars"),
            identifier = Some(identifier2),
            working = Some(true)
          )
        ) >>
        participantEventService.createParticipant( // same host, port, etc.. but we do not care cuz it's only testing - participant identity is based on the identifier either way
          ParticipantModificationPayload(
            host = Some("193.207.130.139"),
            port = Some(3005),
            groupName = Some("notcars"),
            identifier = Some(identifier3),
            working = Some(true)
          )
        )
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> unsuccessfulHealthCheckConnection.use(p => p.source.findAll.toListL)
        >> serviceUnderTest.invokePrivate(healthCheckBackgroundJobPrivateMethod())
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> participantsCache.getAllWorkingParticipants.map { r => firstParticipantsCacheCheck.set(r); () }
        >> unsuccessfulHealthCheckConnection.use(p => p.source.findAll.toListL).map { r =>
          firstUnsuccessfulHealthCheck.set(r); ()
        }

        >> participantEventService.modifyParticipant(
          ParticipantModificationPayload(
            identifier = Some(identifier1),
            healthcheckRoute = Some("notWorking1")
          )
        )
        >> participantEventService.modifyParticipant(
          ParticipantModificationPayload(
            identifier = Some(identifier2),
            healthcheckRoute = Some("notWorking2")
          )
        )

        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> serviceUnderTest.invokePrivate(healthCheckBackgroundJobPrivateMethod())
        >> serviceUnderTest.invokePrivate(healthCheckBackgroundJobPrivateMethod())
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> participantsCache.getAllWorkingParticipants.map { r => secondParticipantsCacheCheck.set(r); () }
        >> unsuccessfulHealthCheckConnection.use(p => p.source.findAll.toListL).map { r =>
          secondUnsuccessfulHealthCheck.set(r); ()
        }

        >> serviceUnderTest.invokePrivate(
          markAsNotWorkingBackgroundJobPrivateMethod()
        ) // nothing changes because not enough time passed
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> participantsCache.getAllWorkingParticipants.map { r => thirdParticipantsCacheCheck.set(r); () }
        >> unsuccessfulHealthCheckConnection.use(p => p.source.findAll.toListL).map { r =>
          thirdUnsuccessfulHealthCheck.set(r); ()
        }

        >> Task.sleep(5.seconds)
        >> serviceUnderTest.invokePrivate(markAsNotWorkingBackgroundJobPrivateMethod())
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> participantsCache.getAllParticipants.map { r => fourthParticipantsCacheCheck.set(r); () }
        >> unsuccessfulHealthCheckConnection.use(p => p.source.findAll.toListL).map { r =>
          fourthUnsuccessfulHealthCheck.set(r); ()
        }
    ).runToFuture

    whenReady(f) { _ =>
      // all 3 participants are healthy
      firstParticipantsCacheCheck.get().size should be(3)
      firstParticipantsCacheCheck.get().map(_.isWorkingAndHealthy) should be(List(true, true, true))
      firstUnsuccessfulHealthCheck.get().size should be(0)

      // there were 2 health-checks performed - our service needs 2 to mark participant as not healthy
      secondParticipantsCacheCheck.get().size should be(3)
      val mapped1 = secondParticipantsCacheCheck
        .get()
        .foldLeft(Map.empty[String, Participant])((prev, it) => prev + (it.identifier -> it))
      mapped1(identifier1).isHealthy should be(false)
      mapped1(identifier2).isHealthy should be(false)
      mapped1(identifier3).isHealthy should be(true)
      secondUnsuccessfulHealthCheck.get().size should be(2)
      secondUnsuccessfulHealthCheck.get().map(_.counter) should be(List(2, 2))
      secondUnsuccessfulHealthCheck.get().map(_.turnedOff) should be(List(false, false))

      // nothing changes because not enough time passed
      thirdParticipantsCacheCheck.get().size should be(3)
      val mapped2 = thirdParticipantsCacheCheck
        .get()
        .foldLeft(Map.empty[String, Participant])((prev, it) => prev + (it.identifier -> it))
      mapped2(identifier1).isHealthy should be(false)
      mapped2(identifier2).isHealthy should be(false)
      mapped2(identifier3).isHealthy should be(true)
      thirdUnsuccessfulHealthCheck.get().size should be(2)
      thirdUnsuccessfulHealthCheck.get().map(_.counter) should be(List(2, 2))
      thirdUnsuccessfulHealthCheck.get().map(_.turnedOff) should be(List(false, false))

      // after 5 seconds
      fourthParticipantsCacheCheck.get().size should be(1)
      fourthParticipantsCacheCheck.get().count(_.isWorkingAndHealthy) should be(1)
      fourthUnsuccessfulHealthCheck.get().size should be(0) // removed immediately
    }
  }

  it should "work when there is no markedAsRemoved setting" in { // the participants are marked as TurnedOff and removed from UnsuccessfulHealthCheck collection immediately, because there is nothing to wait fr
    val serviceUnderTest = new DistributedHealthCheckServiceImpl(
      participantEventService,
      participantsCache,
      nodeTrackerService,
      unsuccessfulHealthCheckDao,
      new ClientStub(),
      new InMemoryBackgroundJobsRegistryImpl(scheduler)
    )(HealthCheckConfig(-1, 2, Some(5), None, Option.empty)) // these values are equal so only markAsRemoved is used

    val identifier1 = randomString("markAsNotWorkingBackgroundJob7")
    val identifier2 = randomString("markAsNotWorkingBackgroundJob8")
    val identifier3 = randomString("markAsNotWorkingBackgroundJob9")

    val firstParticipantsCacheCheck = new AtomicReference[List[Participant]]()
    val secondParticipantsCacheCheck = new AtomicReference[List[Participant]]()
    val thirdParticipantsCacheCheck = new AtomicReference[List[Participant]]()
    val fourthParticipantsCacheCheck = new AtomicReference[List[Participant]]()

    val firstUnsuccessfulHealthCheck = new AtomicReference[List[UnsuccessfulHealthCheck]]()
    val secondUnsuccessfulHealthCheck = new AtomicReference[List[UnsuccessfulHealthCheck]]()
    val thirdUnsuccessfulHealthCheck = new AtomicReference[List[UnsuccessfulHealthCheck]]()
    val fourthUnsuccessfulHealthCheck = new AtomicReference[List[UnsuccessfulHealthCheck]]()

    val f = (
      participantEventService.createParticipant(
        ParticipantModificationPayload(
          host = Some("13.204.158.92"),
          port = Some(3000),
          groupName = Some("cars"),
          identifier = Some(identifier1),
          healthcheckRoute = Some("/api/v1/health"),
          working = Some(true)
        )
      ) >>
        participantEventService.createParticipant(
          ParticipantModificationPayload(
            host = Some("193.207.130.139"),
            port = Some(3005),
            groupName = Some("notcars"),
            identifier = Some(identifier2),
            working = Some(true)
          )
        ) >>
        participantEventService.createParticipant( // same host, port, etc.. but we do not care cuz it's only testing - participant identity is based on the identifier either way
          ParticipantModificationPayload(
            host = Some("193.207.130.139"),
            port = Some(3005),
            groupName = Some("notcars"),
            identifier = Some(identifier3),
            working = Some(true)
          )
        )
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> unsuccessfulHealthCheckConnection.use(p => p.source.findAll.toListL)
        >> serviceUnderTest.invokePrivate(healthCheckBackgroundJobPrivateMethod())
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> participantsCache.getAllWorkingParticipants.map { r => firstParticipantsCacheCheck.set(r); () }
        >> unsuccessfulHealthCheckConnection.use(p => p.source.findAll.toListL).map { r =>
          firstUnsuccessfulHealthCheck.set(r); ()
        }

        >> participantEventService.modifyParticipant(
          ParticipantModificationPayload(
            identifier = Some(identifier1),
            healthcheckRoute = Some("notWorking1")
          )
        )
        >> participantEventService.modifyParticipant(
          ParticipantModificationPayload(
            identifier = Some(identifier2),
            healthcheckRoute = Some("notWorking2")
          )
        )

        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> serviceUnderTest.invokePrivate(healthCheckBackgroundJobPrivateMethod())
        >> serviceUnderTest.invokePrivate(healthCheckBackgroundJobPrivateMethod())
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> participantsCache.getAllWorkingParticipants.map { r => secondParticipantsCacheCheck.set(r); () }
        >> unsuccessfulHealthCheckConnection.use(p => p.source.findAll.toListL).map { r =>
          secondUnsuccessfulHealthCheck.set(r); ()
        }

        >> serviceUnderTest.invokePrivate(
          markAsNotWorkingBackgroundJobPrivateMethod()
        ) // nothing changes because not enough time passed
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> participantsCache.getAllWorkingParticipants.map { r => thirdParticipantsCacheCheck.set(r); () }
        >> unsuccessfulHealthCheckConnection.use(p => p.source.findAll.toListL).map { r =>
          thirdUnsuccessfulHealthCheck.set(r); ()
        }

        >> Task.sleep(5.seconds)
        >> serviceUnderTest.invokePrivate(markAsNotWorkingBackgroundJobPrivateMethod())
        >> participantsCache.invokePrivate(refreshCachePrivateMethod())
        >> participantsCache.getAllParticipants.map { r => fourthParticipantsCacheCheck.set(r); () }
        >> unsuccessfulHealthCheckConnection.use(p => p.source.findAll.toListL).map { r =>
          fourthUnsuccessfulHealthCheck.set(r); ()
        }
    ).runToFuture

    whenReady(f) { _ =>
      // all 3 participants are healthy
      firstParticipantsCacheCheck.get().size should be(3)
      firstParticipantsCacheCheck.get().map(_.isWorkingAndHealthy) should be(List(true, true, true))
      firstUnsuccessfulHealthCheck.get().size should be(0)

      // there were 2 health-checks performed - our service needs 2 to mark participant as not healthy
      secondParticipantsCacheCheck.get().size should be(3)
      val mapped1 = secondParticipantsCacheCheck
        .get()
        .foldLeft(Map.empty[String, Participant])((prev, it) => prev + (it.identifier -> it))
      mapped1(identifier1).isHealthy should be(false)
      mapped1(identifier2).isHealthy should be(false)
      mapped1(identifier3).isHealthy should be(true)
      secondUnsuccessfulHealthCheck.get().size should be(2)
      secondUnsuccessfulHealthCheck.get().map(_.counter) should be(List(2, 2))
      secondUnsuccessfulHealthCheck.get().map(_.turnedOff) should be(List(false, false))

      // nothing changes because not enough time passed
      thirdParticipantsCacheCheck.get().size should be(3)
      val mapped2 = thirdParticipantsCacheCheck
        .get()
        .foldLeft(Map.empty[String, Participant])((prev, it) => prev + (it.identifier -> it))
      mapped2(identifier1).isHealthy should be(false)
      mapped2(identifier2).isHealthy should be(false)
      mapped2(identifier3).isHealthy should be(true)
      thirdUnsuccessfulHealthCheck.get().size should be(2)
      thirdUnsuccessfulHealthCheck.get().map(_.counter) should be(List(2, 2))
      thirdUnsuccessfulHealthCheck.get().map(_.turnedOff) should be(List(false, false))

      // after 5 seconds
      fourthParticipantsCacheCheck.get().size should be(3)
      fourthParticipantsCacheCheck.get().filter(_.isWorking).head.identifier should be(identifier3)
      fourthParticipantsCacheCheck.get().filter(_.isWorkingAndHealthy).head.identifier should be(identifier3)
      fourthParticipantsCacheCheck.get().filterNot(_.isWorking).map(_.identifier) should contain theSameElementsAs List(
        identifier1,
        identifier2
      )
      fourthUnsuccessfulHealthCheck.get().size should be(0) // removed immediately
    }
  }
}
