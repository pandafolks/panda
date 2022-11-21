package com.github.pandafolks.panda.healthcheck

final case class HealthCheckConfig(
    callsInterval: Int, // in seconds
    numberOfFailuresNeededToReact: Int,
    participantIsMarkedAsNotWorkingDelay: Option[Int], // in seconds
    participantIsMarkedAsRemovedDelay: Option[Int], // in seconds
    markedAsNotWorkingJobInterval: Option[Int] // in seconds
) {
  def getParticipantIsMarkedAsNotWorkingDelay: Option[Int] =
    participantIsMarkedAsNotWorkingDelay.flatMap(mapSmallerThanOneToEmpty)

  def getParticipantIsMarkedAsRemovedDelay: Option[Int] =
    participantIsMarkedAsRemovedDelay
      .flatMap(mapSmallerThanOneToEmpty)
      .map(value =>
        if (value >= getParticipantIsMarkedAsNotWorkingDelay.getOrElse(-1)) value
        else getParticipantIsMarkedAsNotWorkingDelay.getOrElse(1)
      )

  def getMarkedAsNotWorkingJobInterval: Option[Int] = {
    val DEFAULT_VALUE = 30
    if (getParticipantIsMarkedAsNotWorkingDelay.isEmpty && getParticipantIsMarkedAsRemovedDelay.isEmpty) Option.empty
    else
      markedAsNotWorkingJobInterval match {
        case None                     => Some(DEFAULT_VALUE)
        case Some(value) if value < 1 => Some(DEFAULT_VALUE)
        case Some(value)              => Some(value)
      }
  }

  private def mapSmallerThanOneToEmpty(value: Int): Option[Int] = if (value < 1) Option.empty else Some(value)
}
