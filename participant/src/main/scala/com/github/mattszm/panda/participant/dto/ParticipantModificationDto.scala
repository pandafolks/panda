package com.github.mattszm.panda.participant.dto

import com.github.mattszm.panda.participant.Participant

final case class ParticipantModificationDto(
                                         host: Option[String] = Option.empty,
                                         port: Option[Int] = Option.empty,
                                         groupName: Option[String] = Option.empty,
                                         identifier: Option[String] = Option.empty,
                                         heartbeatRoute: Option[String] = Option.empty,
                                         working: Option[Boolean] = Option.empty,
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
