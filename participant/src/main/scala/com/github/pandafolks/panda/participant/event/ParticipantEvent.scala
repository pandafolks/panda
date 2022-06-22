package com.github.pandafolks.panda.participant.event

import com.github.pandafolks.panda.participant._
import com.github.pandafolks.panda.routes.Group
import monix.connect.mongodb.client.CollectionCodecRef
import org.bson.UuidRepresentation
import org.bson.codecs.UuidCodec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.BsonInt64
import org.mongodb.scala.bson.codecs.Macros.createCodecProvider

final case class ParticipantEvent(
                                   participantIdentifier: String,
                                   participantDataModification: ParticipantEventDataModification,
                                   eventId: BsonInt64, // the _id precision is in seconds and it is not sufficient
                                   eventType: ParticipantEventType,
                                 ) {
  def convertEventIntoParticipant(participant: Participant, shouldBeSkipped: Boolean = false): (Participant, Boolean) =
    this.eventType match {
      // MainState
      case ParticipantEventType.Created() => // init
        (participant.copy(
          host = participantDataModification.host.getOrElse(""),
          port = participantDataModification.port.getOrElse(-1),
          group = Group(participantDataModification.groupName.getOrElse("")),
          identifier = participantIdentifier,
          heartbeatInfo = HeartbeatInfo(participantDataModification.heartbeatRoute.getOrElse("")),
          status = NotWorking,
        ), false)
      case ParticipantEventType.Removed() => (participant.copy(status = NotWorking), true)

      // SubState
      case ParticipantEventType.TurnedOn() => (participant.copy(status = Working), shouldBeSkipped)
      case ParticipantEventType.TurnedOff() => (participant.copy(status = NotWorking), shouldBeSkipped)

      // ModifiedData
      case ParticipantEventType.ModifiedData() =>
        (participant.copy(
          host = participantDataModification.host.getOrElse(participant.host),
          port = participantDataModification.port.getOrElse(participant.port),
          group = participantDataModification.groupName.map(gn => Group(gn)).getOrElse(participant.group),
          heartbeatInfo = participantDataModification.heartbeatRoute.map(hr => HeartbeatInfo(hr)).getOrElse(participant.heartbeatInfo),
        ), shouldBeSkipped)

      // Connection
      case ParticipantEventType.Joined() => (participant.copy(health = Healthy), shouldBeSkipped)
      case ParticipantEventType.Disconnected() => (participant.copy(health = Unhealthy), shouldBeSkipped)
    }
}

object ParticipantEvent {
  final val PARTICIPANT_EVENTS_COLLECTION_NAME = "participant_events"

  private val javaCodecs = CodecRegistries.fromCodecs(
    new UuidCodec(UuidRepresentation.STANDARD)
  )

  def getCollection(dbName: String, collectionName: String = PARTICIPANT_EVENTS_COLLECTION_NAME): CollectionCodecRef[ParticipantEvent] =
    CollectionCodecRef(
      dbName,
      collectionName,
      classOf[ParticipantEvent],
      fromRegistries(fromProviders(
        classOf[ParticipantEvent],
        classOf[ParticipantEventDataModification],
        classOf[ParticipantEventType]
      ), javaCodecs, DEFAULT_CODEC_REGISTRY)
    )
}