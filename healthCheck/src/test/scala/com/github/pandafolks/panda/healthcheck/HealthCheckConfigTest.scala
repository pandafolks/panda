package com.github.pandafolks.panda.healthcheck

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers.be
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

class HealthCheckConfigTest extends AnyFlatSpec {

  "HealthCheckConfig" should "handle happy path with all legit values" in {
    val underTest = HealthCheckConfig(
      2,
      10,
      Some(10),
      Some(20),
      Some(40)
    )

    underTest.getParticipantIsMarkedAsNotWorkingDelay should be(Some(10))
    underTest.getParticipantIsMarkedAsRemovedDelay should be(Some(20))
    underTest.getMarkedAsNotWorkingJobInterval should be(Some(40))
  }

  "HealthCheckConfig#getParticipantIsMarkedAsNotWorkingDelay" should "return empty when smaller than 1" in {
    val underTest = HealthCheckConfig(
      2,
      10,
      Some(0),
      Some(20),
      Some(40)
    )

    underTest.getParticipantIsMarkedAsNotWorkingDelay should be(Option.empty)
  }

  it should "return empty when the input is empty" in {
    val underTest = HealthCheckConfig(
      2,
      10,
      Option.empty,
      Some(20),
      Some(40)
    )

    underTest.getParticipantIsMarkedAsNotWorkingDelay should be(Option.empty)
  }

  "HealthCheckConfig#getParticipantIsMarkedAsRemovedDelay" should "return empty when smaller than 1" in {
    val underTest = HealthCheckConfig(
      2,
      10,
      Some(20),
      Some(-1),
      Some(40)
    )

    underTest.getParticipantIsMarkedAsRemovedDelay should be(Option.empty)
  }

  it should "return empty when the input is empty" in {
    val underTest = HealthCheckConfig(
      2,
      10,
      Some(123),
      Option.empty,
      Some(40)
    )

    underTest.getParticipantIsMarkedAsRemovedDelay should be(Option.empty)
  }

  it should "set the value to participantIsMarkedAsNotWorkingDelay if is smaller than it" in {
    val underTest = HealthCheckConfig(
      2,
      10,
      Some(123),
      Some(122),
      Some(40)
    )

    underTest.getParticipantIsMarkedAsRemovedDelay should be(Some(123))
  }

  it should "leave the value as it is when participantIsMarkedAsNotWorkingDelay is empty" in {
    val underTest = HealthCheckConfig(
      2,
      10,
      Option.empty,
      Some(122),
      Some(40)
    )

    underTest.getParticipantIsMarkedAsRemovedDelay should be(Some(122))
  }

  it should "leave the value as it is when participantIsMarkedAsNotWorkingDelay is not legit" in {
    val underTest = HealthCheckConfig(
      2,
      10,
      Some(0),
      Some(122),
      Some(40)
    )

    underTest.getParticipantIsMarkedAsRemovedDelay should be(Some(122))
  }

  "HealthCheckConfig#getMarkedAsJobInterval" should "use default value when empty" in {
    val underTest = HealthCheckConfig(
      2,
      10,
      Some(10),
      Some(20),
      Option.empty
    )

    underTest.getParticipantIsMarkedAsNotWorkingDelay should be(Some(10))
    underTest.getParticipantIsMarkedAsRemovedDelay should be(Some(20))
    underTest.getMarkedAsNotWorkingJobInterval should be(Some(30))
  }

  it should "use default value when smaller than 1" in {
    val underTest = HealthCheckConfig(
      2,
      10,
      Some(10),
      Some(20),
      Some(0)
    )

    underTest.getParticipantIsMarkedAsNotWorkingDelay should be(Some(10))
    underTest.getParticipantIsMarkedAsRemovedDelay should be(Some(20))
    underTest.getMarkedAsNotWorkingJobInterval should be(Some(30))
  }

  it should "return empty if both participantIsMarkedAsNotWorkingDelay and participantIsMarkedAsRemovedDelay are not present" in {
    val underTest = HealthCheckConfig(
      2,
      10,
      Option.empty,
      Option.empty,
      Some(123)
    )

    underTest.getParticipantIsMarkedAsNotWorkingDelay should be(Option.empty)
    underTest.getParticipantIsMarkedAsRemovedDelay should be(Option.empty)
    underTest.getMarkedAsNotWorkingJobInterval should be(Option.empty)
  }
}
