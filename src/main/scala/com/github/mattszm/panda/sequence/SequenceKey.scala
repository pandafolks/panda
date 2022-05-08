package com.github.mattszm.panda.sequence

case class SequenceKey(value: String)

object SequenceKey {
  def getParticipantEventSequence: SequenceKey = new SequenceKey("participantEventSequence")

  def getSomeOtherSequence: SequenceKey = new SequenceKey("someOtherSequence")
}
