package com.github.mattszm.panda.participant.event

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
                                 )

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