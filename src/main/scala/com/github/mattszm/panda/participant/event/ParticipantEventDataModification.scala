package com.github.mattszm.panda.participant.event

case class ParticipantEventDataModification(
                                             host: Option[String],
                                             port: Option[Int],
                                             groupName: Option[String],
                                             heartbeatRoute: Option[String]
                                      )

object ParticipantEventDataModification {
  def empty: ParticipantEventDataModification = new ParticipantEventDataModification(None, None, None, None)
}
