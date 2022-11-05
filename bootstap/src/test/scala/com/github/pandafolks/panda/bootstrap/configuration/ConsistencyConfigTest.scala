package com.github.pandafolks.panda.bootstrap.configuration

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers.be
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

import scala.util.Random

class ConsistencyConfigTest extends AnyFlatSpec {
  "ConsistencyConfig#getRealFullConsistencyMaxDelayInMillis" should "return any value smaller by 1 if the starting value is higher or equal to 2 (and convert it to millis)" in {
    val r = new Random(42)
    val configs = for (_ <- 1 to 40) yield ConsistencyConfig(r.between(2, 200))

    configs.foreach { c =>
      c.getRealFullConsistencyMaxDelayInMillis should be((c.fullConsistencyMaxDelay - 1) * 1000)
    }

    ConsistencyConfig(2).getRealFullConsistencyMaxDelayInMillis should be(1000) // edge case
  }

  it should "return 500 millis if the value is smaller or equal to 1" in {
    ConsistencyConfig(1).getRealFullConsistencyMaxDelayInMillis should be(500)
    ConsistencyConfig(0).getRealFullConsistencyMaxDelayInMillis should be(500)
    ConsistencyConfig(-22).getRealFullConsistencyMaxDelayInMillis should be(500)
  }
}
