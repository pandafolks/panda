package com.github.mattszm.panda.participant.event

final case class ParticipantEvent(
                                   participantIdentifier: String,
                                   participantDataModification: ParticipantEventDataModification,
                                   eventId: Long, // the _id precision is in seconds and it is not sufficient
                                   eventType: ParticipantEventType,
                                 )
