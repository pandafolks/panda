package com.github.pandafolks.panda.utils.routing

import com.github.pandafolks.panda.utils.{UndefinedPersistenceError, UnsuccessfulUpdateOperation}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

final class RoutesResultParserTest extends AnyFlatSpec with Matchers {

  "parseSuccessfulResults" should "handle basic scenario" in {
    val input = List(
      Right("valid"),
      Right(""),
      Left(UndefinedPersistenceError("Some error")),
      Right("valid2"),
      Left(UndefinedPersistenceError("Some error 2")),
      Right("  "),
      Left(UnsuccessfulUpdateOperation("Some error 3")),
      Left(UnsuccessfulUpdateOperation("")),
      Left(UnsuccessfulUpdateOperation("    "))
    )

    val expected = List("valid", "valid2", "  ")

    RoutesResultParser.parseSuccessfulResults(input) should contain theSameElementsInOrderAs(expected)
  }

  "parseErrors" should "handle basic scenario" in {
    val input = List(
      Right("valid"),
      Right(""),
      Left(UndefinedPersistenceError("Some error")),
      Right("valid2"),
      Left(UndefinedPersistenceError("Some error 2")),
      Right("  "),
      Left(UnsuccessfulUpdateOperation("Some error 3")),
      Left(UnsuccessfulUpdateOperation("")),
      Left(UnsuccessfulUpdateOperation("    "))
    )

    val expected = List(
      UndefinedPersistenceError("Some error").getMessage,
      UndefinedPersistenceError("Some error 2").getMessage,
      UnsuccessfulUpdateOperation("Some error 3").getMessage,
      UnsuccessfulUpdateOperation("").getMessage,
      UnsuccessfulUpdateOperation("    ").getMessage
    )

    RoutesResultParser.parseErrors(input) should contain theSameElementsInOrderAs (expected)
  }
}
