package com.github.pandafolks.panda.participant.event

import com.github.pandafolks.panda.participant.ParticipantModificationPayload

case class ParticipantEventDataModification(
    host: Option[String],
    port: Option[Int],
    groupName: Option[String],
    healthcheckRoute: Option[String]
)

object ParticipantEventDataModification {
  def empty: ParticipantEventDataModification = new ParticipantEventDataModification(None, None, None, None)

  def of(participantModificationPayload: ParticipantModificationPayload): ParticipantEventDataModification =
    new ParticipantEventDataModification(
      host = participantModificationPayload.host,
      port = participantModificationPayload.port,
      groupName = participantModificationPayload.groupName,
      healthcheckRoute = participantModificationPayload.healthcheckRoute
    )
}
