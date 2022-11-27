package com.github.pandafolks.panda.utils

object EscapeUtils {

  final val PATH_SEPARATOR: Char = '/'

  /** Trim and remove any slashes at the beginning and ending of the input string.
    *
    * @param input
    *   String to process
    * @return
    *   Result
    */
  def unifyFromSlashes(input: String): String = input.trim.dropWhile(_ == PATH_SEPARATOR).reverse.dropWhile(_ == PATH_SEPARATOR).reverse
}
