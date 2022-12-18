package com.github.pandafolks.panda.healthcheck

final case class HealthCheckConfig(
    callsInterval: Int, // in seconds
    numberOfFailuresNeededToReact: Int,
    participantIsMarkedAsTurnedOffDelay: Option[Int], // in seconds
    participantIsMarkedAsRemovedDelay: Option[Int], // in seconds
    markedAsNotWorkingJobInterval: Option[Int] // in seconds
) {

  def healthCheckEnabled: Boolean = callsInterval > 0 && numberOfFailuresNeededToReact > 0

  def getParticipantIsMarkedAsTurnedOffDelay: Option[Int] =
    participantIsMarkedAsTurnedOffDelay
      .flatMap(mapSmallerThanOneToEmpty)
      // if there is participantIsMarkedAsTurnedOffDelay bigger or equal to participantIsMarkedAsRemovedDelay, there should be just participantIsMarkedAsRemovedDelay
      .filter(_ < getParticipantIsMarkedAsRemovedDelay.getOrElse(Int.MaxValue))

  def getParticipantIsMarkedAsTurnedOffDelayInMillis: Option[Int] = getParticipantIsMarkedAsTurnedOffDelay.map(_ * 1000)

  def getParticipantIsMarkedAsRemovedDelay: Option[Int] =
    participantIsMarkedAsRemovedDelay.flatMap(mapSmallerThanOneToEmpty)

  def getParticipantIsMarkedAsRemovedDelayInMillis: Option[Int] = getParticipantIsMarkedAsRemovedDelay.map(_ * 1000)

  def getSmallerMarkedAsDelay: Option[Int] =
    (getParticipantIsMarkedAsTurnedOffDelay, getParticipantIsMarkedAsRemovedDelay) match {
      case (Some(turnedOffDelay), Some(removedDelay)) =>
        if (turnedOffDelay < removedDelay) Some(turnedOffDelay) else Some(removedDelay)
      case (Some(turnedOffDelay), None) => Some(turnedOffDelay)
      case (None, Some(removedDelay))   => Some(removedDelay)
      case (None, None)                 => Option.empty
    }

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
