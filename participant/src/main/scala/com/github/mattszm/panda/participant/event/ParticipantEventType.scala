package com.github.mattszm.panda.participant.event

sealed trait ParticipantEventType

object ParticipantEventType {
  sealed trait MainState extends ParticipantEventType
  case class Created() extends MainState
  case class Removed() extends MainState

  sealed trait SubState extends ParticipantEventType
  case class TurnedOn() extends ParticipantEventType
  case class TurnedOff() extends ParticipantEventType

  case class ModifiedData() extends ParticipantEventType

  sealed trait Connection extends ParticipantEventType
  case class Joined() extends Connection
  case class Disconnected() extends Connection
}
