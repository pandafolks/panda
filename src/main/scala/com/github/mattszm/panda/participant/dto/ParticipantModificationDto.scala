package com.github.mattszm.panda.participant.dto

import com.github.mattszm.panda.participant.Participant

final case class ParticipantModificationDto(
                                         host: Option[String],
                                         port: Option[Int],
                                         groupName: Option[String],
                                         identifier: Option[String],
                                         heartbeatRoute: Option[String],
                                         working: Option[Boolean],
                                       ) {
  def getIdentifier: Option[String] =
    identifier.orElse(
      for {
        h <- host
        p <- port
        g <- groupName
      } yield Participant.createDefaultIdentifier(h, p, g)
    )
}
