package com.github.mattszm.panda.participant.event

import org.mongodb.scala.bson.BsonInt32

final case class ParticipantEvent(
                                   participantIdentifier: String,
                                   participantDataModification: ParticipantEventDataModification,
                                   eventId: BsonInt32, // the _id precision is in seconds and it is not sufficient
                                   eventType: ParticipantEventType,
                                 )
