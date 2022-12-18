package com.github.pandafolks.panda.utils

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers.be
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

class EscapeUtilsTest extends AnyFlatSpec {

  "EscapeUtils#unifyFromSlashes" should "trim and remove any slashes at the beginning and ending" in {

    EscapeUtils.unifyFromSlashes("  /something/something2/ ") should be("something/something2")
    EscapeUtils.unifyFromSlashes("/something3/something4/") should be("something3/something4")
    EscapeUtils.unifyFromSlashes("  /something5/something6") should be("something5/something6")
    EscapeUtils.unifyFromSlashes("  something7/something8 ") should be("something7/something8")
    EscapeUtils.unifyFromSlashes(" something9/something10") should be("something9/something10")
  }
}
