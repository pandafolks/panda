package com.github.mattszm.panda.participant.dto

final case class ParticipantCreationDto(
                                         host: String,
                                         port: Int,
                                         groupName: String,
                                         identifier: Option[String],
                                         heartbeatRoute: Option[String],
                                         working: Option[Boolean],
                                       )
