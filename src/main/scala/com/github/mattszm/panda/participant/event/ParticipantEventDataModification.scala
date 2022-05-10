package com.github.mattszm.panda.participant.event

import com.github.mattszm.panda.participant.dto.ParticipantModificationDto

case class ParticipantEventDataModification(
                                             host: Option[String],
                                             port: Option[Int],
                                             groupName: Option[String],
                                             heartbeatRoute: Option[String]
                                      )

object ParticipantEventDataModification {
  def empty: ParticipantEventDataModification = new ParticipantEventDataModification(None, None, None, None)

  def of(dto: ParticipantModificationDto): ParticipantEventDataModification =
    new ParticipantEventDataModification(
      host = dto.host,
      port = dto.port,
      groupName = dto.groupName,
      heartbeatRoute = dto.heartbeatRoute
    )
}
