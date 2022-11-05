package com.github.pandafolks.panda.utils

object EscapeUtils {

  /** Trim and remove any slashes at the beginning and ending of the input string.
    *
    * @param input
    *   String to process
    * @return
    *   Result
    */
  def unifyFromSlashes(input: String): String = input.trim.dropWhile(_ == '/').reverse.dropWhile(_ == '/').reverse
}
