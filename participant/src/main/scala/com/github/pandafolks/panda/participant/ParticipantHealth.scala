package com.github.pandafolks.panda.participant

sealed trait ParticipantHealth
case object Healthy extends ParticipantHealth
case object Unhealthy extends ParticipantHealth
