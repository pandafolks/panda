package com.github.mattszm.panda.participant

sealed trait ParticipantStatus

case object Working extends ParticipantStatus
case object NotWorking extends ParticipantStatus
