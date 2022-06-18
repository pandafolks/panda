package com.github.pandafolks.panda.participant

sealed trait ParticipantStatus

case object Working extends ParticipantStatus
case object NotWorking extends ParticipantStatus
