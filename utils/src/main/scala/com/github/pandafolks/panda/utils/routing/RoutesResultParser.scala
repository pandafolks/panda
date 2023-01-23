package com.github.pandafolks.panda.utils.routing

import com.github.pandafolks.panda.utils.PersistenceError

object RoutesResultParser {

  def parseSuccessfulResults(input: Seq[Either[PersistenceError, String]]): List[String] =
    input
      .map(_.getOrElse(""))
      .filter(_.nonEmpty)
      .toList

  def parseErrors(input: Seq[Either[PersistenceError, String]]): List[String] =
    input
      .map(_.left.map(_.getMessage).left.getOrElse(""))
      .filter(_.nonEmpty)
      .toList
}
