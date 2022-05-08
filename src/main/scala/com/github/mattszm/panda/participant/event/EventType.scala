package com.github.mattszm.panda.participant.event

sealed trait EventType

object EventType {
  case class Created() extends EventType

  case class Joined() extends EventType
}
