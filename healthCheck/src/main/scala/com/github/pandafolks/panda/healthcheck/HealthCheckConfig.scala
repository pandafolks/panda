package com.github.pandafolks.panda.healthcheck

final case class HealthCheckConfig(
    callsInterval: Int, // in seconds
    numberOfFailuresNeededToReact: Int,
    participantIsMarkedAsTurnedOffDelay: Option[Int], // in seconds
    participantIsMarkedAsRemovedDelay: Option[Int], // in seconds
    markedAsNotWorkingJobInterval: Option[Int] // in seconds
) {
  def getParticipantIsMarkedAsTurnedOffDelay: Option[Int] =
    participantIsMarkedAsTurnedOffDelay
      .flatMap(mapSmallerThanOneToEmpty)
      // if there is participantIsMarkedAsTurnedOffDelay bigger or equal to participantIsMarkedAsRemovedDelay, there should be just participantIsMarkedAsRemovedDelay
      .filter(_ < getParticipantIsMarkedAsRemovedDelay.getOrElse(Int.MaxValue))

  def getParticipantIsMarkedAsRemovedDelay: Option[Int] =
    participantIsMarkedAsRemovedDelay.flatMap(mapSmallerThanOneToEmpty)

  def getMarkedAsNotWorkingJobInterval: Option[Int] = {
    val DEFAULT_VALUE = 30
    if (getParticipantIsMarkedAsTurnedOffDelay.isEmpty && getParticipantIsMarkedAsRemovedDelay.isEmpty) Option.empty
    else
      markedAsNotWorkingJobInterval match {
        case None                     => Some(DEFAULT_VALUE)
        case Some(value) if value < 1 => Some(DEFAULT_VALUE)
        case Some(value)              => Some(value)
      }
  }

  private def mapSmallerThanOneToEmpty(value: Int): Option[Int] = if (value < 1) Option.empty else Some(value)
}
