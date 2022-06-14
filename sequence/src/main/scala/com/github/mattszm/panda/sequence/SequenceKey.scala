package com.github.mattszm.panda.sequence

final case class SequenceKey(value: String)

object SequenceKey {
  def getParticipantEventSequence: SequenceKey = new SequenceKey("participantEventSequence")

  def getSomeOtherSequence: SequenceKey = new SequenceKey("someOtherSequence") // dead code
}
