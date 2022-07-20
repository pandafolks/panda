package com.github.pandafolks.panda.utils

object EscapeUtils {
  def unifyFromSlashes(prefix: String): String = prefix.dropWhile(_ == '/').reverse.dropWhile(_ == '/').reverse
}
