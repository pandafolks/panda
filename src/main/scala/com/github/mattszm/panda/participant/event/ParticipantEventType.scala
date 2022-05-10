package com.github.mattszm.panda.participant.event

sealed trait ParticipantEventType

object ParticipantEventType {
  sealed trait MainState extends ParticipantEventType
  case class Created() extends MainState
  case class Removed() extends MainState

  case class ModifiedData() extends ParticipantEventType

  sealed trait Connection extends ParticipantEventType
  case class Joined() extends Connection
  case class Disconnected() extends Connection
}
