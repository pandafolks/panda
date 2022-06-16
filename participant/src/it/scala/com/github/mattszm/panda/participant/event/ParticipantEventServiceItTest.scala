package com.github.mattszm.panda.participant.event

import com.github.mattszm.panda.participant.Participant
import com.github.mattszm.panda.participant.Participant.HEARTBEAT_DEFAULT_ROUTE
import com.github.mattszm.panda.participant.dto.ParticipantModificationDto
import com.github.mattszm.panda.utils.{AlreadyExists, NotExists}
import com.mongodb.client.model.Filters
import monix.execution.Scheduler
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, EitherValues}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class ParticipantEventServiceItTest extends AsyncFlatSpec with ParticipantEventFixture with Matchers with ScalaFutures
  with EitherValues with BeforeAndAfterAll with BeforeAndAfterEach {
  implicit val scheduler: Scheduler = Scheduler.io("participant-event-service-it-test")

  implicit val defaultConfig: PatienceConfig = PatienceConfig(30.seconds, 100.milliseconds)

  override protected def afterAll(): Unit = mongoContainer.stop()

  override protected def beforeEach(): Unit = Await.result(participantEventsAndSequencesConnection.use {
    case (p, _) => p.db.dropCollection(participantEventsColName)
  }.runToFuture, 5.seconds)

  "createParticipant" should "insert Created event and assign default identifier" in {
    val defaultIdentifier = Participant.createDefaultIdentifier("127.0.0.1", 1001, "cars")

    val f = participantEventService.createParticipant(ParticipantModificationDto(
      host = Some("127.0.0.1"), port = Some(1001), groupName = Some("cars"), identifier = Option.empty, heartbeatRoute = Option.empty, working = Some(false)
    )).flatMap(res =>
      participantEventsAndSequencesConnection.use(p =>
        p._1.source.find(Filters.eq("participantIdentifier", defaultIdentifier)).toListL
      ).map(events => (res, events)))
      .runToFuture

    whenReady(f) { res =>
      res._1.toOption.get should be(defaultIdentifier)
      res._2.size should be(1)
      res._2.head.eventType should be(ParticipantEventType.Created())
      res._2.head.participantDataModification.heartbeatRoute should be(Some(HEARTBEAT_DEFAULT_ROUTE))
      res._2.head.participantDataModification.host should be(Some("127.0.0.1"))
      res._2.head.participantDataModification.port should be(Some(1001))
      res._2.head.participantDataModification.groupName should be(Some("cars"))
    }
  }

  it should "insert Created event, use specified by user identifier and insert TurnedOn event (working attribute set to true)" in {
    val identifier = randomString("identifier")

    val f = participantEventService.createParticipant(ParticipantModificationDto(
      host = Some("127.0.0.1"), port = Some(1002), groupName = Some("planes"), identifier = Some(identifier), heartbeatRoute = Option.empty, working = Some(true)
    )).flatMap(res =>
      participantEventsAndSequencesConnection.use(p =>
        p._1.source.find(Filters.eq("participantIdentifier", identifier)).toListL
      ).map(events => (res, events)))
      .runToFuture

    whenReady(f) { res =>
      res._1.toOption.get should be(identifier)
      res._2.size should be(2)
      res._2.minBy(_.eventId).eventType should be(ParticipantEventType.Created())
      res._2.sortBy(_.eventId).tail.head.eventType should be(ParticipantEventType.TurnedOn())
    }
  }

  it should "insert Created event, insert TurnedOn event (working attribute not set) and apply custom heartbeat path" in {
    val identifier = randomString("identifier")

    val f = participantEventService.createParticipant(ParticipantModificationDto(
      host = Some("127.0.0.1"), port = Some(1002), groupName = Some("planes"), identifier = Some(identifier), heartbeatRoute = Some("/api/check"), working = Option.empty
    )).flatMap(res =>
      participantEventsAndSequencesConnection.use(p =>
        p._1.source.find(Filters.eq("participantIdentifier", identifier)).toListL
      ).map(events => (res, events)))
      .runToFuture

    whenReady(f) { res =>
      res._1.toOption.get should be(identifier)
      res._2.size should be(2)
      res._2.minBy(_.eventId).eventType should be(ParticipantEventType.Created())
      res._2.minBy(_.eventId).participantDataModification.heartbeatRoute should be(Some("/api/check"))
      res._2.sortBy(_.eventId).tail.head.eventType should be(ParticipantEventType.TurnedOn())
    }
  }

  it should "fail if there are no required attributes" in {
    val identifier = randomString("identifier")

    val f = participantEventService.createParticipant(ParticipantModificationDto(
      host = Some("127.0.0.1"), port = Option.empty, groupName = Some("planes"), identifier = Some(identifier), heartbeatRoute = Some("/api/check"), working = Option.empty
    )).flatMap(res =>
      participantEventsAndSequencesConnection.use(p =>
        p._1.source.find(Filters.eq("participantIdentifier", identifier)).toListL
      ).map(events => (res, events)))
      .runToFuture

    whenReady(f) { res =>
      res._1.isLeft should be(true)
      res._2.size should be(0)
    }
  }

  it should "fail if there is already a participant with requested identifier" in {
    val identifier = randomString("identifier")

    val f = participantEventService.createParticipant(ParticipantModificationDto(
      host = Some("127.0.0.1"), port = Some(131313), groupName = Some("planes"), identifier = Some(identifier), heartbeatRoute = Some("/api/check"), working = Option.empty
    )).flatMap(_ =>
      participantEventService.createParticipant(ParticipantModificationDto(
        host = Some("127.0.0.1"), port = Some(131213), groupName = Some("planes"), identifier = Some(identifier), heartbeatRoute = Some("/api/check2"), working = Option.empty
      ))
    ).runToFuture

    whenReady(f) { res =>
      res.isLeft should be(true)
      res should be(Left(AlreadyExists("Participant with identifier \"" + identifier + "\" already exists")))
    }
  }

  it should "insert Created event if there is already a participant with requested identifier, but there was a " +
    "Removed event emitted after last Created event occurrence" in {
    val identifier = randomString("identifier")

    val f = participantEventService.createParticipant(ParticipantModificationDto(
      host = Some("127.0.0.1"), port = Some(131313), groupName = Some("planes"), identifier = Some(identifier), heartbeatRoute = Some("/api/check"), working = Some(false)
    )).flatMap(_ =>
      participantEventService.removeParticipant(identifier)
    ).flatMap(_ =>
      participantEventService.createParticipant(ParticipantModificationDto(
        host = Some("127.0.0.1"), port = Some(141414), groupName = Some("planes"), identifier = Some(identifier), heartbeatRoute = Some("/api/check"), working = Some(false)
      ))
    ).flatMap(res =>
      participantEventsAndSequencesConnection.use(p =>
        p._1.source.find(Filters.eq("participantIdentifier", identifier)).toListL.map(_.sortBy(event => -event.eventId.intValue()))
      ).map(events => (res, events))
    ).runToFuture

    whenReady(f) { res =>
      res._1.toOption.get should be(identifier)
      res._2.size should be(3)
      res._2.head.eventType should be(ParticipantEventType.Created())
      res._2.head.participantDataModification.heartbeatRoute should be(Some("/api/check"))
      res._2.head.participantDataModification.host should be(Some("127.0.0.1"))
      res._2.head.participantDataModification.port should be(Some(141414))
      res._2.head.participantDataModification.groupName should be(Some("planes"))
    }
  }

  "modifyParticipant" should "fail if there is participant with requested identifier" in {
    val identifier = randomString("identifier")

    val f = participantEventService.modifyParticipant(ParticipantModificationDto(
      host = Some("127.0.0.1"), port = Option.empty, groupName = Some("planes"), identifier = Some(identifier), heartbeatRoute = Some("/api/check"), working = Option.empty
    )).flatMap(res =>
      participantEventsAndSequencesConnection.use(p =>
        p._1.source.find(Filters.eq("participantIdentifier", identifier)).toListL
      ).map(events => (res, events)))
      .runToFuture

    whenReady(f) { res =>
      res._1.isLeft should be(true)
      res._2.size should be(0)
    }
  }

  it should "insert ModifiedData event and be able to recognize participant based on default identifier" in {
    val defaultIdentifier = Participant.createDefaultIdentifier("127.0.0.2", 1002, "ships")

    val f = participantEventService.createParticipant(ParticipantModificationDto(
      host = Some("127.0.0.2"), port = Some(1002), groupName = Some("ships"), identifier = Option.empty, heartbeatRoute = Option.empty, working = Some(false)
    ))
      .flatMap(_ =>
        participantEventService.modifyParticipant(ParticipantModificationDto(
          host = Option.empty, port = Option.empty, groupName = Option.empty, identifier = Some(defaultIdentifier), heartbeatRoute = Some("SomePath/"), working = Option.empty
        ))
    )
      .flatMap(res =>
      participantEventsAndSequencesConnection.use(p =>
        p._1.source.find(Filters.eq("participantIdentifier", defaultIdentifier)).toListL
      ).map(events => (res, events)))
      .runToFuture

    whenReady(f) { res =>
      res._1.toOption.get should be(defaultIdentifier)
      res._2.size should be(2)
      res._2.minBy(_.eventId).eventType should be(ParticipantEventType.Created())
      res._2.minBy(_.eventId).participantDataModification.heartbeatRoute should be(Some(HEARTBEAT_DEFAULT_ROUTE))
      res._2.sortBy(_.eventId).tail.head.eventType should be(ParticipantEventType.ModifiedData())
      res._2.sortBy(_.eventId).tail.head.participantDataModification.heartbeatRoute should be(Some("SomePath/"))
    }
  }

  it should "insert ModifiedData event and TurnedOn event because there was a 'working' attribute set to true" in {
    val identifier = randomString("identifier")

    val f = participantEventService.createParticipant(ParticipantModificationDto(
      host = Some("127.0.0.1"), port = Some(1002), groupName = Some("planes"), identifier = Some(identifier), heartbeatRoute = Option.empty, working = Some(false)
    ))
      .flatMap(_ =>
        participantEventService.modifyParticipant(ParticipantModificationDto(
          host = Option.empty, port = Option.empty, groupName = Option.empty, identifier = Some(identifier), heartbeatRoute = Option.empty, working = Some(true)
        ))
      )
      .flatMap(res =>
      participantEventsAndSequencesConnection.use(p =>
        p._1.source.find(Filters.eq("participantIdentifier", identifier)).toListL
      ).map(events => (res, events)))
      .runToFuture

    whenReady(f) { res =>
      res._1.toOption.get should be(identifier)

      res._2.size should be(3)
      res._2.minBy(_.eventId).eventType should be(ParticipantEventType.Created())
      res._2.minBy(_.eventId).participantIdentifier should be(identifier)

      res._2.sortBy(_.eventId).tail.head.eventType should be(ParticipantEventType.ModifiedData())
      res._2.sortBy(_.eventId).tail.head.participantIdentifier should be(identifier)

      res._2.sortBy(_.eventId).tail.last.eventType should be(ParticipantEventType.TurnedOn())
      res._2.sortBy(_.eventId).tail.last.participantIdentifier should be(identifier)
    }
  }

  it should "insert ModifiedData event and TurnedOff event because there was a 'working' attribute set to false" in {
    val identifier = randomString("identifier")

    val f = participantEventService.createParticipant(ParticipantModificationDto(
      host = Some("127.0.0.1"), port = Some(1002), groupName = Some("planes"), identifier = Some(identifier), heartbeatRoute = Option.empty, working = Some(false)
    ))
      .flatMap(_ =>
        participantEventService.modifyParticipant(ParticipantModificationDto(
          host = Some("127.0.0.2"), port = Some(1003), groupName = Some("ships"), identifier = Some(identifier), heartbeatRoute = Option.empty, working = Some(false)
        ))
      )
      .flatMap(res =>
        participantEventsAndSequencesConnection.use(p =>
          p._1.source.find(Filters.eq("participantIdentifier", identifier)).toListL
        ).map(events => (res, events)))
      .runToFuture

    whenReady(f) { res =>
      res._1.toOption.get should be(identifier)

      res._2.size should be(3)
      res._2.minBy(_.eventId).eventType should be(ParticipantEventType.Created())
      res._2.minBy(_.eventId).participantDataModification.host should be(Some("127.0.0.1"))
      res._2.minBy(_.eventId).participantDataModification.port should be(Some(1002))
      res._2.minBy(_.eventId).participantDataModification.groupName should be(Some("planes"))

      res._2.sortBy(_.eventId).tail.head.eventType should be(ParticipantEventType.ModifiedData())
      res._2.sortBy(_.eventId).tail.head.participantDataModification.host should be(Some("127.0.0.2"))
      res._2.sortBy(_.eventId).tail.head.participantDataModification.port should be(Some(1003))
      res._2.sortBy(_.eventId).tail.head.participantDataModification.groupName should be(Some("ships"))
      res._2.sortBy(_.eventId).tail.head.participantDataModification.heartbeatRoute should be(Option.empty)


      res._2.sortBy(_.eventId).tail.last.eventType should be(ParticipantEventType.TurnedOff())
      res._2.sortBy(_.eventId).tail.last.participantIdentifier should be(identifier)
    }
  }

  it should "not insert any event if the identifier is not specified explicitly and there is a try to modify host/port/groupName" in {
    val defaultIdentifier = Participant.createDefaultIdentifier("127.0.0.4", 1006, "ships")

    val f = participantEventService.createParticipant(ParticipantModificationDto(
      host = Some("127.0.0.4"), port = Some(1006), groupName = Some("ships"), identifier = Option.empty, heartbeatRoute = Option.empty, working = Some(false)
    ))
      .flatMap(_ =>
        participantEventService.modifyParticipant(ParticipantModificationDto(
          host = Some("127.0.0.9"), port = Some(1234), groupName = Some("ships2"), identifier = Option.empty, heartbeatRoute = Some("SomePath2/"), working = Option.empty
        ))
      )
      .flatMap(res =>
        participantEventsAndSequencesConnection.use(p =>
          p._1.source.find(Filters.eq("participantIdentifier", defaultIdentifier)).toListL
        ).map(events => (res, events)))
      .runToFuture

    whenReady(f) { res =>
      res._1 should be(Left(NotExists("Participant with identifier \"" + Participant.createDefaultIdentifier("127.0.0.9", 1234, "ships2") + "\" does not exist")))

      res._2.size should be(1)
      res._2.minBy(_.eventId).eventType should be(ParticipantEventType.Created())
      res._2.minBy(_.eventId).participantIdentifier should be(defaultIdentifier)
      res._2.minBy(_.eventId).participantDataModification.host should be(Some("127.0.0.4"))
      res._2.minBy(_.eventId).participantDataModification.port should be(Some(1006))
      res._2.minBy(_.eventId).participantDataModification.groupName should be(Some("ships"))
      res._2.minBy(_.eventId).participantDataModification.heartbeatRoute should be(Some(HEARTBEAT_DEFAULT_ROUTE))
    }
  }

  "removeParticipant" should "insert Removed event if the participant with requested identifier exists - not matter what happened before" in {
    val defaultIdentifier = Participant.createDefaultIdentifier("127.1.1.4", 1026, "ships")

    val f = participantEventService.createParticipant(ParticipantModificationDto(
      host = Some("127.1.1.4"), port = Some(1026), groupName = Some("ships"), identifier = Option.empty, heartbeatRoute = Some("api/hb"), working = Some(false)
    ))
      .flatMap(_ => participantEventService.removeParticipant(defaultIdentifier))
      .flatMap(res =>
        participantEventsAndSequencesConnection.use(p =>
          p._1.source.find(Filters.eq("participantIdentifier", defaultIdentifier)).toListL
        ).map(events => (res, events)))
      .runToFuture

    whenReady(f) { res =>
      res._1.toOption.get should be(defaultIdentifier)

      res._2.size should be(2)
      res._2.minBy(_.eventId).eventType should be(ParticipantEventType.Created())
      res._2.minBy(_.eventId).participantIdentifier should be(defaultIdentifier)
      res._2.minBy(_.eventId).participantDataModification.host should be(Some("127.1.1.4"))
      res._2.minBy(_.eventId).participantDataModification.port should be(Some(1026))
      res._2.minBy(_.eventId).participantDataModification.groupName should be(Some("ships"))
      res._2.minBy(_.eventId).participantDataModification.heartbeatRoute should be(Some("api/hb"))

      res._2.sortBy(_.eventId).tail.head.eventType should be(ParticipantEventType.Removed())
      res._2.sortBy(_.eventId).tail.head.participantDataModification.host should be(Option.empty)
      res._2.sortBy(_.eventId).tail.head.participantDataModification.port should be(Option.empty)
      res._2.sortBy(_.eventId).tail.head.participantDataModification.groupName should be(Option.empty)
      res._2.sortBy(_.eventId).tail.head.participantDataModification.heartbeatRoute should be(Option.empty)
    }
  }

  it should "return error if there is no participant with requested identifier" in {
    val identifier = randomString("identifier")
    val f = participantEventService.removeParticipant(identifier).runToFuture

    whenReady(f) { res =>
      res should be(Left(NotExists("Participant with identifier \"" + identifier + "\" does not exist")))
    }
  }
}
