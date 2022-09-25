package com.github.pandafolks.panda.utils

object SystemProperties {
  def scalaConcurrentContextMinThreads: String = System.getProperty("scala.concurrent.context.minThreads")

  def scalaConcurrentContextMaxThreads: String = System.getProperty("scala.concurrent.context.maxThreads")

  def scalaConcurrentContextNumThreads: String = System.getProperty("scala.concurrent.context.numThreads")

  /**
   * A private key used to sign users' authentication tokens.
   *
   * Example: -Dpanda.user.token.key=5ck4kBO45606H25YUZ1f
   */
  def usersTokenKey: String = System.getProperty("panda.user.token.key")
}
