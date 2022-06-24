package com.github.pandafolks.panda.participant.event

import com.github.pandafolks.panda.participant.dto.ParticipantModificationDto

case class ParticipantEventDataModification(
                                             host: Option[String],
                                             port: Option[Int],
                                             groupName: Option[String],
                                             healthcheckRoute: Option[String]
                                      )

object ParticipantEventDataModification {
  def empty: ParticipantEventDataModification = new ParticipantEventDataModification(None, None, None, None)

  def of(dto: ParticipantModificationDto): ParticipantEventDataModification =
    new ParticipantEventDataModification(
      host = dto.host,
      port = dto.port,
      groupName = dto.groupName,
      healthcheckRoute = dto.healthcheckRoute
    )
}
