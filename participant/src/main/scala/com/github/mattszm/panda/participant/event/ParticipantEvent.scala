package com.github.mattszm.panda.participant.event

import com.github.mattszm.panda.participant.{HeartbeatInfo, NotWorking, Participant, Working}
import com.github.mattszm.panda.routes.Group
import monix.connect.mongodb.client.CollectionCodecRef
import org.bson.UuidRepresentation
import org.bson.codecs.UuidCodec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.BsonInt32
import org.mongodb.scala.bson.codecs.Macros.createCodecProvider

final case class ParticipantEvent(
                                   participantIdentifier: String,
                                   participantDataModification: ParticipantEventDataModification,
                                   eventId: BsonInt32, // the _id precision is in seconds and it is not sufficient
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
      case ParticipantEventType.Joined() => (participant.copy(status = Working), shouldBeSkipped)
      case ParticipantEventType.Disconnected() => (participant.copy(status = NotWorking), shouldBeSkipped)
    }
}

object ParticipantEvent {
  final val PARTICIPANT_EVENTS_COLLECTION_NAME = "participant_events"

  private val javaCodecs = CodecRegistries.fromCodecs(
    new UuidCodec(UuidRepresentation.STANDARD)
  )

  def getCollection(dbName: String): CollectionCodecRef[ParticipantEvent] = CollectionCodecRef(
    dbName,
    PARTICIPANT_EVENTS_COLLECTION_NAME,
    classOf[ParticipantEvent],
    fromRegistries(fromProviders(
      classOf[ParticipantEvent],
      classOf[ParticipantEventDataModification],
      classOf[ParticipantEventType]
    ), javaCodecs, DEFAULT_CODEC_REGISTRY)
  )
}